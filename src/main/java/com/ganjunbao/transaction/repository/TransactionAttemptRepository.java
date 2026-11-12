package com.ganjunbao.transaction.repository;

import com.ganjunbao.transaction.mapper.TransactionAttemptMapper;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.TransactionAttempt;
import com.ganjunbao.transaction.model.TransactionAttemptStatus;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class TransactionAttemptRepository {

    private final TransactionAttemptMapper transactionAttemptMapper;

    public TransactionAttemptRepository(
            TransactionAttemptMapper transactionAttemptMapper
    ) {
        this.transactionAttemptMapper = transactionAttemptMapper;
    }

    public void recordSuccess(
            Transaction transaction,
            String action
    ) {
        int attemptNo = transactionAttemptMapper
                .findNextAttemptNo(transaction.getDatabaseId());

        OffsetDateTime now = OffsetDateTime.now();

        TransactionAttempt attempt = new TransactionAttempt(
                transaction.getDatabaseId(),
                transaction.getChannelId(),
                attemptNo,
                TransactionAttemptStatus.SUCCEEDED,
                "{\"action\":\"" + action + "\"}",
                "{\"result\":\"SUCCESS\"}",
                null,
                null,
                now,
                now
        );

        transactionAttemptMapper.insert(attempt);
    }

    public List<TransactionAttempt> findByTransactionId(
            Long transactionId
    ) {
        return transactionAttemptMapper
                .findByTransactionId(transactionId);
    }
}