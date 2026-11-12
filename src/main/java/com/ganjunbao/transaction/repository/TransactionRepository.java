package com.ganjunbao.transaction.repository;

import com.ganjunbao.transaction.exception.DuplicateIdempotencyKeyException;
import com.ganjunbao.transaction.mapper.TransactionMapper;
import com.ganjunbao.transaction.model.Transaction;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class TransactionRepository {

    private final TransactionMapper transactionMapper;

    public TransactionRepository(TransactionMapper transactionMapper) {
        this.transactionMapper = transactionMapper;
    }

    public Transaction insert(Transaction transaction) {
        try {
            transactionMapper.insert(transaction);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateIdempotencyKeyException(
                    transaction.getIdempotencyKey()
            );
        }

        return findById(transaction.getId())
                .orElseThrow(() ->
                        new IllegalStateException("交易写入后无法查询")
                );
    }

    public Optional<Transaction> findById(String transactionNo) {
        return transactionMapper.findByTransactionNo(transactionNo);
    }

    public Transaction updateStatus(Transaction transaction) {
        int updatedRows = transactionMapper.updateStatus(transaction);

        if (updatedRows == 0) {
            throw new IllegalStateException("交易状态更新失败");
        }

        return findById(transaction.getId())
                .orElseThrow(() ->
                        new IllegalStateException("交易更新后无法查询")
                );
    }
}