package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.dto.CreateTransactionRequest;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.TransactionStatus;
import com.ganjunbao.transaction.repository.OutboxEventRepository;
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
class TransactionRollbackIntegrationTest extends PostgresContainerTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionFailureExperimentService failureExperimentService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRollbackTransactionAndOutboxWhenExceptionOccurs() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("99.00"));
        request.setDescription("事务回滚实验");
        request.setIdempotencyKey("rollback-" + UUID.randomUUID());
        request.setCurrency("CNY");
        request.setChannelCode("DEMO");

        Transaction created = transactionService.create(request);

        try {
            assertEquals(TransactionStatus.CREATED, created.getStatus());
            assertEquals(
                    0,
                    outboxEventRepository.countByAggregateId(created.getId())
            );

            assertThrows(
                    IllegalStateException.class,
                    () -> failureExperimentService.confirmThenFail(
                            created.getId()
                    )
            );

            Transaction afterFailure =
                    transactionService.getById(created.getId());

            assertEquals(
                    TransactionStatus.CREATED,
                    afterFailure.getStatus()
            );

            assertEquals(
                    0,
                    outboxEventRepository.countByAggregateId(created.getId())
            );
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM payment_transaction WHERE transaction_no = ?",
                    created.getId()
            );
        }
    }
}
