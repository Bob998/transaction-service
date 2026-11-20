package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.dto.CreateTransactionRequest;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.reconciliation.ReconciliationSummary;
import com.ganjunbao.transaction.reconciliation.DifferenceResolution;
import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ReconciliationIntegrationTest extends PostgresContainerTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldFindAllThreeKindsOfDifferences() {
        Transaction amountMismatch = createSucceeded("21.00");
        Transaction channelMissing = createSucceeded("32.00");
        LocalDate businessDate = LocalDate.now(ZoneOffset.UTC);
        var startAt = businessDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        var endAt = businessDate.plusDays(1)
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC);

        Map<String, BigDecimal> channelStatement = new HashMap<>();
        List<Map<String, Object>> successfulTransactions =
                jdbcTemplate.queryForList("""
                        SELECT transaction_no, amount
                        FROM payment_transaction
                        WHERE status = 'SUCCEEDED'
                          AND created_at >= ?
                          AND created_at < ?
                        """, startAt, endAt);
        for (Map<String, Object> row : successfulTransactions) {
            channelStatement.put(
                    row.get("transaction_no").toString(),
                    new BigDecimal(row.get("amount").toString())
            );
        }
        channelStatement.put(
                amountMismatch.getId(),
                new BigDecimal("20.00")
        );
        channelStatement.remove(channelMissing.getId());
        channelStatement.put(
                "TXN-CHANNEL-ONLY-" + UUID.randomUUID(),
                new BigDecimal("45.00")
        );

        ReconciliationSummary summary = reconciliationService.reconcile(
                businessDate,
                channelStatement
        );

        assertEquals(3, summary.differenceCount());
        assertEquals(
                1,
                differenceCount(summary.batchId(), "AMOUNT_MISMATCH")
        );
        assertEquals(
                1,
                differenceCount(summary.batchId(), "CHANNEL_MISSING")
        );
        assertEquals(
                1,
                differenceCount(summary.batchId(), "LOCAL_MISSING")
        );
        assertEquals(
                channelMissing.getId(),
                jdbcTemplate.queryForObject(
                        """
                        SELECT transaction_no
                        FROM reconciliation_difference
                        WHERE batch_id = ?
                          AND difference_type = 'CHANNEL_MISSING'
                        """,
                        String.class,
                        summary.batchId()
                )
        );

        Long differenceId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM reconciliation_difference
                WHERE batch_id = ?
                  AND difference_type = 'AMOUNT_MISMATCH'
                """,
                Long.class,
                summary.batchId()
        );

        reconciliationService.resolveDifference(
                differenceId,
                DifferenceResolution.RESOLVED,
                "reconciliation-operator",
                "已向通道核实，以本地金额为准"
        );

        assertEquals(
                "RESOLVED",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM reconciliation_difference WHERE id = ?",
                        String.class,
                        differenceId
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM audit_log
                        WHERE action = 'RECONCILIATION_DIFFERENCE_RESOLVED'
                          AND operator = 'reconciliation-operator'
                        """,
                        Integer.class
                )
        );
        assertThrows(
                IllegalStateException.class,
                () -> reconciliationService.resolveDifference(
                        differenceId,
                        DifferenceResolution.IGNORED,
                        "second-operator",
                        "重复处理"
                )
        );
    }

    private Transaction createSucceeded(String amount) {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal(amount));
        request.setDescription("日终对账测试");
        request.setIdempotencyKey("reconcile-" + UUID.randomUUID());
        request.setCurrency("CNY");
        request.setChannelCode("DEMO");

        Transaction created = transactionService.create(request);
        transactionService.confirm(created.getId());
        transactionService.startProcessing(created.getId());
        return transactionService.succeed(created.getId());
    }

    private int differenceCount(long batchId, String differenceType) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM reconciliation_difference
                WHERE batch_id = ?
                  AND difference_type = ?
                """,
                Integer.class,
                batchId,
                differenceType
        );
    }
}
