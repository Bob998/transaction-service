package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.dto.CreateTransactionRequest;
import com.ganjunbao.transaction.exception.InvalidTransactionStateException;
import com.ganjunbao.transaction.exception.TransactionNotFoundException;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.TransactionStatus;
import com.ganjunbao.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Transaction create(CreateTransactionRequest request) {
        Transaction transaction = new Transaction(
                UUID.randomUUID().toString(),
                request.getAmount(),
                request.getDescription(),
                TransactionStatus.PENDING,
                LocalDateTime.now()
        );
        return transactionRepository.save(transaction);
    }

    public Transaction getById(String id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    public Transaction confirm(String id) {
        Transaction transaction = getById(id);
        if (transaction.getStatus() == TransactionStatus.PENDING) {
            throw new InvalidTransactionStateException("只有待处理交易才能确认");
        }

        transaction.confirm();
        return transactionRepository.save(transaction);
    }

    public Transaction cancel(String id) {
        Transaction transaction = getById(id);
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new InvalidTransactionStateException("只有待处理交易才能取消");
        }

        transaction.cancel();
        return transactionRepository.save(transaction);
    }
}
