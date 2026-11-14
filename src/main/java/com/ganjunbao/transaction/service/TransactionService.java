package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.dto.CreateTransactionRequest;
import com.ganjunbao.transaction.exception.ChannelNotAvailableException;
import com.ganjunbao.transaction.exception.InvalidTransactionStateException;
import com.ganjunbao.transaction.exception.TransactionNotFoundException;
import com.ganjunbao.transaction.model.PaymentChannel;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.TransactionStatus;
import com.ganjunbao.transaction.repository.AuditLogRepository;
import com.ganjunbao.transaction.repository.OutboxEventRepository;
import com.ganjunbao.transaction.repository.PaymentChannelRepository;
import com.ganjunbao.transaction.repository.TransactionAttemptRepository;
import com.ganjunbao.transaction.repository.TransactionRepository;
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

    public TransactionService(
            TransactionRepository transactionRepository,
            PaymentChannelRepository paymentChannelRepository,
            TransactionAttemptRepository transactionAttemptRepository,
            AuditLogRepository auditLogRepository,
            OutboxEventRepository outboxEventRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.paymentChannelRepository = paymentChannelRepository;
        this.transactionAttemptRepository = transactionAttemptRepository;
        this.auditLogRepository = auditLogRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    public Transaction create(CreateTransactionRequest request) {
        String channelCode = request.getChannelCode()
                .toUpperCase(Locale.ROOT);

        PaymentChannel channel = paymentChannelRepository
                .findActiveByCode(channelCode)
                .orElseThrow(() ->
                        new ChannelNotAvailableException(channelCode)
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

        return transactionRepository.insert(transaction);
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

        return updated;
    }
}
