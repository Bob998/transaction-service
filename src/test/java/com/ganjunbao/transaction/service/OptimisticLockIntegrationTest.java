package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.dto.CreateTransactionRequest;
import com.ganjunbao.transaction.exception.OptimisticLockConflictException;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.TransactionStatus;
import com.ganjunbao.transaction.repository.TransactionRepository;
import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OptimisticLockIntegrationTest extends PostgresContainerTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRejectUpdateUsingStaleVersion() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("288.00"));
        request.setDescription("乐观锁冲突测试");
        request.setIdempotencyKey("optimistic-" + UUID.randomUUID());
        request.setCurrency("CNY");
        request.setChannelCode("DEMO");

        Transaction created = transactionService.create(request);

        try {
            Transaction firstSnapshot =
                    transactionService.getById(created.getId());

            Transaction secondSnapshot =
                    transactionService.getById(created.getId());

            assertEquals(0L, firstSnapshot.getVersion());
            assertEquals(0L, secondSnapshot.getVersion());

            firstSnapshot.approveRisk();

            Transaction firstUpdated =
                    transactionRepository.updateStatus(firstSnapshot);

            assertEquals(1L, firstUpdated.getVersion());
            assertEquals(
                    TransactionStatus.RISK_APPROVED,
                    firstUpdated.getStatus()
            );

            secondSnapshot.approveRisk();

            assertThrows(
                    OptimisticLockConflictException.class,
                    () -> transactionRepository.updateStatus(
                            secondSnapshot
                    )
            );

            Transaction finalTransaction =
                    transactionService.getById(created.getId());

            assertEquals(1L, finalTransaction.getVersion());
            assertEquals(
                    TransactionStatus.RISK_APPROVED,
                    finalTransaction.getStatus()
            );
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM payment_transaction WHERE id = ?",
                    created.getDatabaseId()
            );
        }
    }
}
