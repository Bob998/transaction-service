package com.ganjunbao.transaction.mapper;

import com.ganjunbao.transaction.model.Transaction;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTransactionMapper implements TransactionMapper {

    private final Map<String, Transaction> storage =
            new ConcurrentHashMap<>();

    @Override
    public int insert(Transaction transaction) {
        storage.put(transaction.getId(), transaction);
        return 1;
    }

    @Override
    public Optional<Transaction> findByTransactionNo(
            String transactionNo
    ) {
        return Optional.ofNullable(storage.get(transactionNo));
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(
            String idempotencyKey
    ) {
        return storage.values().stream()
                .filter(transaction -> idempotencyKey.equals(
                        transaction.getIdempotencyKey()
                ))
                .findFirst();
    }

    @Override
    public int updateStatus(Transaction transaction) {
        if (!storage.containsKey(transaction.getId())) {
            return 0;
        }

        storage.put(transaction.getId(), transaction);
        return 1;
    }
}
