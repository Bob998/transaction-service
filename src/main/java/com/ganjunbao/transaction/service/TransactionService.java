package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.dto.CreateTransactionRequest;
import com.ganjunbao.transaction.exception.DuplicateIdempotencyKeyException;
import com.ganjunbao.transaction.exception.InvalidTransactionStateException;
import com.ganjunbao.transaction.exception.RiskControlUnavailableException;
import com.ganjunbao.transaction.exception.RiskRejectedException;
import com.ganjunbao.transaction.exception.TransactionNotFoundException;
import com.ganjunbao.transaction.model.PaymentChannel;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.TransactionStatus;
import com.ganjunbao.transaction.repository.AuditLogRepository;
import com.ganjunbao.transaction.repository.OutboxEventRepository;
import com.ganjunbao.transaction.repository.PaymentChannelRepository;
import com.ganjunbao.transaction.repository.TransactionAttemptRepository;
import com.ganjunbao.transaction.repository.TransactionRepository;
import com.ganjunbao.transaction.repository.UserRiskProfileRepository;
import com.ganjunbao.transaction.repository.LedgerEntryRepository;
import com.ganjunbao.transaction.redis.IdempotencyResultCache;
import com.ganjunbao.transaction.redis.RiskSlidingWindowService;
import com.ganjunbao.transaction.redis.RiskWindowResult;
import com.ganjunbao.transaction.risk.RiskAssessment;
import com.ganjunbao.transaction.risk.RiskAssessmentService;
import com.ganjunbao.transaction.risk.RiskContext;
import com.ganjunbao.transaction.risk.UserRiskProfile;
import com.ganjunbao.transaction.observability.BusinessMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final PaymentChannelRepository paymentChannelRepository;
    private final TransactionAttemptRepository transactionAttemptRepository;
    private final AuditLogRepository auditLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyResultCache idempotencyResultCache;
    private final RiskSlidingWindowService riskSlidingWindowService;
    private final ChannelQuotaGuard channelQuotaGuard;
    private final DynamicChannelRouter dynamicChannelRouter;
    private final UserRiskProfileRepository userRiskProfileRepository;
    private final RiskAssessmentService riskAssessmentService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private BusinessMetrics businessMetrics;

    @Autowired
    public void setBusinessMetrics(BusinessMetrics businessMetrics) {
        this.businessMetrics = businessMetrics;
    }

    public TransactionService(
            TransactionRepository transactionRepository,
            PaymentChannelRepository paymentChannelRepository,
            TransactionAttemptRepository transactionAttemptRepository,
            AuditLogRepository auditLogRepository,
            OutboxEventRepository outboxEventRepository,
            IdempotencyResultCache idempotencyResultCache,
            RiskSlidingWindowService riskSlidingWindowService,
            ChannelQuotaGuard channelQuotaGuard,
            DynamicChannelRouter dynamicChannelRouter,
            UserRiskProfileRepository userRiskProfileRepository,
            RiskAssessmentService riskAssessmentService,
            LedgerEntryRepository ledgerEntryRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.paymentChannelRepository = paymentChannelRepository;
        this.transactionAttemptRepository = transactionAttemptRepository;
        this.auditLogRepository = auditLogRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.idempotencyResultCache = idempotencyResultCache;
        this.riskSlidingWindowService = riskSlidingWindowService;
        this.channelQuotaGuard = channelQuotaGuard;
        this.dynamicChannelRouter = dynamicChannelRouter;
        this.userRiskProfileRepository = userRiskProfileRepository;
        this.riskAssessmentService = riskAssessmentService;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public Transaction create(CreateTransactionRequest request) {
        return create(request, null);
    }

    @Transactional
    public Transaction create(CreateTransactionRequest request, String userId) {
        Transaction cachedTransaction = idempotencyResultCache
                .get(request.getIdempotencyKey())
                .orElse(null);
        if (cachedTransaction != null) {
            validateIdempotentReplay(request, cachedTransaction);
            return cachedTransaction;
        }

        Transaction existingTransaction = transactionRepository
                .findByIdempotencyKey(request.getIdempotencyKey())
                .orElse(null);
        if (existingTransaction != null) {
            validateIdempotentReplay(request, existingTransaction);
            idempotencyResultCache.put(existingTransaction);
            return existingTransaction;
        }

        evaluateConfigurableRisk(userId, request);

        ChannelRouteDecision routeDecision = dynamicChannelRouter.select(
                request.getCurrency().toUpperCase(Locale.ROOT),
                request.getAmount()
        );
        PaymentChannel channel = routeDecision.channel();
        String channelCode = channel.getChannelCode();

        channelQuotaGuard.reserve(
                channelCode,
                request.getAmount(),
                request.getIdempotencyKey()
        );

        OffsetDateTime now = OffsetDateTime.now();

        Transaction transaction = new Transaction(
                "TXN-" + UUID.randomUUID(),
                request.getIdempotencyKey(),
                userId,
                channel.getId(),
                request.getAmount(),
                request.getCurrency().toUpperCase(Locale.ROOT),
                request.getDescription(),
                TransactionStatus.CREATED,
                now,
                now
        );

        Transaction createdTransaction;
        boolean newlyCreated;

        try {
            createdTransaction = transactionRepository.insert(transaction);
            newlyCreated = true;
        } catch (DuplicateIdempotencyKeyException exception) {
            createdTransaction = transactionRepository
                    .findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow(() -> exception);
            validateIdempotentReplay(request, createdTransaction);

            newlyCreated = false;
        }

        if (newlyCreated) {
            outboxEventRepository.record(
                    "TRANSACTION",
                    createdTransaction.getId(),
                    "TRANSACTION_CREATED",
                    "{\"transactionId\":\""
                            + createdTransaction.getId()
                            + "\",\"status\":\"CREATED\"}"
            );
            if (businessMetrics != null) {
                businessMetrics.transactionCreated(channelCode);
            }
        }

        idempotencyResultCache.put(createdTransaction);
        return createdTransaction;
    }

    private void validateIdempotentReplay(
            CreateTransactionRequest request,
            Transaction existingTransaction
    ) {
        boolean sameAmount = existingTransaction.getAmount()
                .compareTo(request.getAmount()) == 0;
        boolean sameCurrency = existingTransaction.getCurrency()
                .equalsIgnoreCase(request.getCurrency());
        boolean sameDescription = existingTransaction.getDescription()
                .equals(request.getDescription());

        if (!sameAmount || !sameCurrency || !sameDescription) {
            throw new DuplicateIdempotencyKeyException(
                    request.getIdempotencyKey(),
                    "请求内容与首次请求不一致"
            );
        }
    }

    private void evaluateConfigurableRisk(
            String userId,
            CreateTransactionRequest request
    ) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        RiskWindowResult windowResult;
        try {
            windowResult = riskSlidingWindowService.evaluateAndRecord(
                    userId,
                    request.getIdempotencyKey(),
                    request.getAmount()
            );
        } catch (DataAccessException exception) {
            if (businessMetrics != null) {
                businessMetrics.redisFailure("risk_window");
            }
            throw new RiskControlUnavailableException(exception);
        }

        if (!windowResult.allowed()) {
            recordRiskDecision("rejected");
            throw new RiskRejectedException(
                    windowResult.transactionCount(),
                    windowResult.totalAmount()
            );
        }

        UserRiskProfile profile =
                userRiskProfileRepository.findOrDefault(userId);
        BigDecimal dailyAmount =
                transactionRepository.sumDailyAmount(userId);
        long recentTransactionCount = Math.max(
                windowResult.transactionCount(),
                transactionRepository.countRecentTransactions(userId)
        );
        long recentFailureCount =
                transactionRepository.countRecentFailures(userId);

        RiskContext context = new RiskContext(
                request.getIdempotencyKey(),
                userId,
                profile.getUserLevel(),
                request.getAmount(),
                request.getCurrency().toUpperCase(Locale.ROOT),
                dailyAmount,
                profile.getDailyLimit(),
                recentTransactionCount,
                recentFailureCount
        );

        RiskAssessment assessment =
                riskAssessmentService.evaluateAndRecord(context);

        if (assessment.rejected()) {
            recordRiskDecision("rejected");
            throw new RiskRejectedException(
                    "交易被Aviator风控规则拒绝，userId=" + userId
            );
        }

        if (assessment.requiresReview()) {
            recordRiskDecision("review");
            throw new RiskRejectedException(
                    "交易需要人工审核，userId=" + userId
            );
        }
        recordRiskDecision("approved");
    }

    private void recordRiskDecision(String decision) {
        if (businessMetrics != null) {
            businessMetrics.riskDecision(decision);
        }
    }

    public Transaction getById(String id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    @Transactional
    public Transaction confirm(String id) {
        Transaction transaction = getById(id);
        if (transaction.getStatus() != TransactionStatus.CREATED) {
            throw new InvalidTransactionStateException(
                    "只有 CREATED 状态的交易才能通过风控"
            );
        }
        TransactionStatus oldStatus = transaction.getStatus();
        transaction.approveRisk();
        Transaction updated = transactionRepository.updateStatus(transaction);
        transactionAttemptRepository.recordSuccess(updated, "CONFIRM");
        auditLogRepository.recordStatusChange(
                updated,
                oldStatus,
                TransactionStatus.RISK_APPROVED,
                "TRANSACTION_RISK_APPROVED"
        );
        outboxEventRepository.record(
                "TRANSACTION",
                updated.getId(),
                "TRANSACTION_RISK_APPROVED",
                "{\"transactionId\":\"" + updated.getId()
                        + "\",\"status\":\"RISK_APPROVED\"}"
        );
        idempotencyResultCache.evict(updated.getIdempotencyKey());
        return updated;
    }

    @Transactional
    public Transaction cancel(String id) {
        Transaction transaction = getById(id);

        if (transaction.getStatus() != TransactionStatus.CREATED
                && transaction.getStatus() != TransactionStatus.RISK_APPROVED) {
            throw new InvalidTransactionStateException(
                    "只有 CREATED 或 RISK_APPROVED 状态的交易才能取消"
            );
        }

        TransactionStatus oldStatus = transaction.getStatus();
        transaction.cancel();
        Transaction updated = transactionRepository.updateStatus(transaction);
        transactionAttemptRepository.recordSuccess(updated, "CANCEL");
        auditLogRepository.recordStatusChange(
                updated,
                oldStatus,
                TransactionStatus.CANCELLED,
                "TRANSACTION_CANCELLED"
        );
        outboxEventRepository.record(
                "TRANSACTION",
                updated.getId(),
                "TRANSACTION_CANCELLED",
                "{\"transactionId\":\"" + updated.getId()
                        + "\",\"status\":\"CANCELLED\"}"
        );
        idempotencyResultCache.evict(updated.getIdempotencyKey());
        return updated;
    }

    @Transactional
    public Transaction startProcessing(String id) {
        Transaction transaction = getById(id);

        if (transaction.getStatus() != TransactionStatus.RISK_APPROVED) {
            throw new InvalidTransactionStateException(
                    "只有 RISK_APPROVED 状态的交易才能开始处理"
            );
        }

        TransactionStatus oldStatus = transaction.getStatus();
        transaction.startProcessing();

        Transaction updated = transactionRepository.updateStatus(transaction);

        transactionAttemptRepository.recordSuccess(
                updated,
                "START_PROCESSING"
        );

        auditLogRepository.recordStatusChange(
                updated,
                oldStatus,
                TransactionStatus.PROCESSING,
                "TRANSACTION_PROCESSING"
        );
        outboxEventRepository.record(
                "TRANSACTION",
                updated.getId(),
                "TRANSACTION_PROCESSING",
                "{\"transactionId\":\"" + updated.getId()
                        + "\",\"status\":\"PROCESSING\"}"
        );

        idempotencyResultCache.evict(updated.getIdempotencyKey());

        return updated;
    }

    @Transactional
    public Transaction succeed(String id) {
        Transaction transaction = getById(id);

        if (transaction.getStatus() != TransactionStatus.PROCESSING) {
            throw new InvalidTransactionStateException(
                    "只有 PROCESSING 状态的交易才能成功"
            );
        }

        TransactionStatus oldStatus = transaction.getStatus();
        transaction.succeed();

        Transaction updated = transactionRepository.updateStatus(transaction);

        // 状态更新和账务流水受同一个 @Transactional 事务保护。
        ledgerEntryRepository.recordPayment(updated);

        transactionAttemptRepository.recordSuccess(
                updated,
                "SUCCEED"
        );

        auditLogRepository.recordStatusChange(
                updated,
                oldStatus,
                TransactionStatus.SUCCEEDED,
                "TRANSACTION_SUCCEEDED"
        );
        outboxEventRepository.record(
                "TRANSACTION",
                updated.getId(),
                "TRANSACTION_SUCCEEDED",
                "{\"transactionId\":\"" + updated.getId()
                        + "\",\"status\":\"SUCCEEDED\"}"
        );

        idempotencyResultCache.evict(updated.getIdempotencyKey());

        return updated;
    }

    @Transactional
    public Transaction fail(String id) {
        Transaction transaction = getById(id);

        if (transaction.getStatus() != TransactionStatus.PROCESSING) {
            throw new InvalidTransactionStateException(
                    "只有 PROCESSING 状态的交易才能失败"
            );
        }

        TransactionStatus oldStatus = transaction.getStatus();
        transaction.fail();

        Transaction updated = transactionRepository.updateStatus(transaction);

        transactionAttemptRepository.recordSuccess(
                updated,
                "FAIL"
        );

        auditLogRepository.recordStatusChange(
                updated,
                oldStatus,
                TransactionStatus.FAILED,
                "TRANSACTION_FAILED"
        );
        outboxEventRepository.record(
                "TRANSACTION",
                updated.getId(),
                "TRANSACTION_FAILED",
                "{\"transactionId\":\"" + updated.getId()
                        + "\",\"status\":\"FAILED\"}"
        );

        idempotencyResultCache.evict(updated.getIdempotencyKey());

        return updated;
    }
}
