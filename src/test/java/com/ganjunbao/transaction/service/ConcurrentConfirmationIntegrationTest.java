package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.dto.CreateTransactionRequest;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.TransactionStatus;
import com.ganjunbao.transaction.repository.OutboxEventRepository;
import com.ganjunbao.transaction.repository.TransactionAttemptRepository;
import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ConcurrentConfirmationIntegrationTest extends PostgresContainerTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionAttemptRepository transactionAttemptRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldOnlyConfirmTransactionOnceUnderConcurrency() throws Exception {

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("188.00"));
        request.setDescription("并发确认测试");
        request.setIdempotencyKey("concurrent-" + UUID.randomUUID());
        request.setCurrency("CNY");
        request.setChannelCode("DEMO");

        Transaction created = transactionService.create(request);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> {
                ready.countDown();
                start.await();

                try {
                    transactionService.confirm(created.getId());
                    return true;
                } catch (RuntimeException exception) {
                    return false;
                }
            });

            Future<Boolean> second = executor.submit(() -> {
                ready.countDown();
                start.await();

                try {
                    transactionService.confirm(created.getId());
                    return true;
                } catch (RuntimeException exception) {
                    return false;
                }
            });

            ready.await();
            start.countDown();

            int successCount = 0;

            if (first.get()) {
                successCount++;
            }

            if (second.get()) {
                successCount++;
            }

            assertEquals(1, successCount);

            Transaction finalTransaction =
                    transactionService.getById(created.getId());

            assertEquals(
                    TransactionStatus.RISK_APPROVED,
                    finalTransaction.getStatus()
            );

            assertEquals(
                    1,
                    transactionAttemptRepository
                            .countSuccessfulConfirmAttempts(
                                    finalTransaction.getDatabaseId()
                            )
            );

            assertEquals(
                    1,
                    outboxEventRepository.countByAggregateId(
                            finalTransaction.getId()
                    )
            );
        } finally {
            executor.shutdownNow();

            jdbcTemplate.update(
                    "DELETE FROM outbox_event WHERE aggregate_id = ?",
                    created.getId()
            );

            jdbcTemplate.update(
                    "DELETE FROM transaction_attempt WHERE transaction_id = ?",
                    created.getDatabaseId()
            );

            jdbcTemplate.update(
                    "DELETE FROM audit_log WHERE transaction_id = ?",
                    created.getDatabaseId()
            );

            jdbcTemplate.update(
                    "DELETE FROM payment_transaction WHERE id = ?",
                    created.getDatabaseId()
            );
        }
    }
}
