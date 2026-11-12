package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.dto.CreateTransactionRequest;
import com.ganjunbao.transaction.exception.ChannelNotAvailableException;
import com.ganjunbao.transaction.exception.InvalidTransactionStateException;
import com.ganjunbao.transaction.exception.TransactionNotFoundException;
import com.ganjunbao.transaction.model.PaymentChannel;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.TransactionStatus;
import com.ganjunbao.transaction.repository.AuditLogRepository;
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

    public TransactionService(
            TransactionRepository transactionRepository,
            PaymentChannelRepository paymentChannelRepository,
            TransactionAttemptRepository transactionAttemptRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.paymentChannelRepository = paymentChannelRepository;
        this.transactionAttemptRepository = transactionAttemptRepository;
        this.auditLogRepository = auditLogRepository;
    }

//    public Transaction create(CreateTransactionRequest request) {
//        Transaction transaction = new Transaction(
//                UUID.randomUUID().toString(),
//                request.getAmount(),
//                request.getDescription(),
//                TransactionStatus.PENDING,
//                OffsetDateTime.now()
//        );
//        return transactionRepository.save(transaction);
//    }

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
                TransactionStatus.PENDING,
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
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new InvalidTransactionStateException(
                    "只有待处理交易才能确认"
            );
        }
        TransactionStatus oldStatus = transaction.getStatus();
        transaction.confirm();
        Transaction updated = transactionRepository.updateStatus(transaction);
        transactionAttemptRepository.recordSuccess(updated, "CONFIRM");
        auditLogRepository.recordStatusChange(
                updated,
                oldStatus,
                TransactionStatus.CONFIRMED,
                "TRANSACTION_CONFIRMED"
        );
        return updated;
    }

    @Transactional
    public Transaction cancel(String id) {
        Transaction transaction = getById(id);

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new InvalidTransactionStateException(
                    "只有待处理交易才能取消"
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
        return updated;
    }
}
