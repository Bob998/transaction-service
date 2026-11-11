package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.dto.CreateTransactionRequest;
import com.ganjunbao.transaction.exception.InvalidTransactionStateException;
import com.ganjunbao.transaction.exception.TransactionNotFoundException;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.TransactionStatus;
import com.ganjunbao.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

public class TransactionServiceTest {
    private TransactionService transactionService;

    @BeforeEach
    public void setUp() {
        TransactionRepository transactionRepository = new TransactionRepository();
        transactionService = new TransactionService(transactionRepository);
    }

    @Test
    void shouldCreatePendingTransaction() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("100.50"));
        request.setDescription("购买商品");

        Transaction transaction = transactionService.create(request);

        assertNotNull(transaction.getId());
        assertEquals(new BigDecimal("100.50"), transaction.getAmount());
        assertEquals("购买商品", transaction.getDescription());
        assertEquals(TransactionStatus.PENDING, transaction.getStatus());
        assertNotNull(transaction.getCreatedAt());
    }

    @Test
    void shouldFindCreatedTransactionById() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("200.00"));
        request.setDescription("查询测试");

        Transaction created = transactionService.create(request);
        Transaction found = transactionService.getById(created.getId());

        assertEquals(created.getId(), found.getId());
        assertEquals(new BigDecimal("200.00"), found.getAmount());
        assertEquals("查询测试", found.getDescription());
        assertEquals(TransactionStatus.PENDING, found.getStatus());
    }

    @Test
    void shouldConfirmPendingTransaction() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("300.00"));
        request.setDescription("确认测试");

        Transaction created = transactionService.create(request);
        Transaction confirmed = transactionService.confirm(created.getId());

        assertEquals(TransactionStatus.CONFIRMED, confirmed.getStatus());
    }

    @Test
    void shouldCancelPendingTransaction() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("88.00"));
        request.setDescription("取消测试");

        Transaction created = transactionService.create(request);
        Transaction cancelled = transactionService.cancel(created.getId());

        assertEquals(TransactionStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void shouldThrowWhenTransactionDoesNotExist() {
        assertThrows(
                TransactionNotFoundException.class,
                () -> transactionService.getById("not-exist")
        );
    }

    @Test
    void shouldRejectConfirmingConfirmedTransaction() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("500.00"));
        request.setDescription("重复确认测试");

        Transaction created = transactionService.create(request);
        transactionService.confirm(created.getId());

        assertThrows(
                InvalidTransactionStateException.class,
                () -> transactionService.confirm(created.getId())
        );
    }

    @Test
    void shouldRejectCancellingConfirmedTransaction() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("600.00"));
        request.setDescription("确认后取消测试");

        Transaction created = transactionService.create(request);
        transactionService.confirm(created.getId());

        assertThrows(
                InvalidTransactionStateException.class,
                () -> transactionService.cancel(created.getId())
        );
    }
}
