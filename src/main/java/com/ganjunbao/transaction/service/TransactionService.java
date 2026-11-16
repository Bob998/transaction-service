package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.dto.CreateTransactionRequest;
import com.ganjunbao.transaction.exception.ChannelNotAvailableException;
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
import com.ganjunbao.transaction.redis.IdempotencyResultCache;
import com.ganjunbao.transaction.redis.RiskSlidingWindowService;
import com.ganjunbao.transaction.redis.RiskWindowResult;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public TransactionService(
            TransactionRepository transactionRepository,
            PaymentChannelRepository paymentChannelRepository,
            TransactionAttemptRepository transactionAttemptRepository,
            AuditLogRepository auditLogRepository,
            OutboxEventRepository outboxEventRepository,
            IdempotencyResultCache idempotencyResultCache,
            RiskSlidingWindowService riskSlidingWindowService,
            ChannelQuotaGuard channelQuotaGuard
    ) {
        this.transactionRepository = transactionRepository;
        this.paymentChannelRepository = paymentChannelRepository;
        this.transactionAttemptRepository = transactionAttemptRepository;
        this.auditLogRepository = auditLogRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.idempotencyResultCache = idempotencyResultCache;
        this.riskSlidingWindowService = riskSlidingWindowService;
        this.channelQuotaGuard = channelQuotaGuard;
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
            return cachedTransaction;
        }

        evaluateRiskWhenUserIsKnown(userId, request);

        String channelCode = request.getChannelCode()
                .toUpperCase(Locale.ROOT);

        PaymentChannel channel = paymentChannelRepository
                .findActiveByCode(channelCode)
                .orElseThrow(() ->
                        new ChannelNotAvailableException(channelCode)
                );

        channelQuotaGuard.reserve(
                channelCode,
                request.getAmount(),
                request.getIdempotencyKey()
        );

        OffsetDateTime now = OffsetDateTime.now();

        Transaction transaction = new Transaction(
                "TXN-" + UUID.randomUUID(),
                request.getIdempotencyKey(),
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
        }

        idempotencyResultCache.put(createdTransaction);
        return createdTransaction;
    }

    private void evaluateRiskWhenUserIsKnown(
            String userId,
            CreateTransactionRequest request
    ) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        RiskWindowResult riskResult;
        try {
            riskResult = riskSlidingWindowService.evaluateAndRecord(
                    userId,
                    request.getIdempotencyKey(),
                    request.getAmount()
            );
        } catch (DataAccessException exception) {
            throw new RiskControlUnavailableException(exception);
        }

        if (!riskResult.allowed()) {
            throw new RiskRejectedException(
                    riskResult.transactionCount(),
                    riskResult.totalAmount()
            );
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
