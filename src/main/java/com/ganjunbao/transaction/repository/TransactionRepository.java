package com.ganjunbao.transaction.repository;

import com.ganjunbao.transaction.model.Transaction;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class TransactionRepository {
    private final Map<String, Transaction> storage = new ConcurrentHashMap<>();

    public Transaction save(Transaction transaction) {
        storage.put(transaction.getId(), transaction);
        return transaction;
    }

    public Optional<Transaction> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }
}
