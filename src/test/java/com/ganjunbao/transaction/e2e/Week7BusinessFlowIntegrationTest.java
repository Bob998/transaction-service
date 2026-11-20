package com.ganjunbao.transaction.e2e;

import com.ganjunbao.transaction.dto.CreateTransactionRequest;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.TransactionStatus;
import com.ganjunbao.transaction.reconciliation.ReconciliationSummary;
import com.ganjunbao.transaction.service.ReconciliationService;
import com.ganjunbao.transaction.service.TransactionService;
import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class Week7BusinessFlowIntegrationTest extends PostgresContainerTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCompleteFinancialBusinessLoop() {
        String userId = "week7-user-" + UUID.randomUUID();
        CreateTransactionRequest request = request();

        // 创建阶段包含：幂等检查、用户额度与规则风控、动态通道路由。
        Transaction created = transactionService.create(request, userId);
        Transaction duplicate = transactionService.create(request, userId);

        assertEquals(created.getId(), duplicate.getId());
        assertEquals(TransactionStatus.CREATED, created.getStatus());
        assertNotNull(created.getChannelId());
        assertEquals(
                1,
                count("payment_transaction", "transaction_no", created.getId())
        );

        // 模拟风险通过、通道处理成功，成功状态与账务流水同事务落库。
        transactionService.confirm(created.getId());
        transactionService.startProcessing(created.getId());
        Transaction succeeded = transactionService.succeed(created.getId());

        assertEquals(TransactionStatus.SUCCEEDED, succeeded.getStatus());
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM ledger_entry
                        WHERE transaction_id = ?
                          AND entry_type = 'PAYMENT'
                        """,
                        Integer.class,
                        succeeded.getDatabaseId()
                )
        );
        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE transaction_id = ?",
                        Integer.class,
                        succeeded.getDatabaseId()
                )
        );
        assertEquals(
                4,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ?",
                        Integer.class,
                        succeeded.getId()
                )
        );

        // 用当天全部成功交易构造一致的通道账单，验证日终闭环无差异。
        LocalDate businessDate = LocalDate.now(ZoneOffset.UTC);
        var startAt = businessDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        var endAt = businessDate.plusDays(1)
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC);
        Map<String, BigDecimal> channelStatement = new HashMap<>();
        jdbcTemplate.queryForList("""
                SELECT transaction_no, amount
                FROM payment_transaction
                WHERE status = 'SUCCEEDED'
                  AND created_at >= ?
                  AND created_at < ?
                """, startAt, endAt).forEach(row -> channelStatement.put(
                row.get("transaction_no").toString(),
                new BigDecimal(row.get("amount").toString())
        ));

        ReconciliationSummary summary = reconciliationService.reconcile(
                businessDate,
                channelStatement
        );

        assertEquals(0, summary.differenceCount());
    }

    private CreateTransactionRequest request() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("88.80"));
        request.setDescription("第七周完整业务闭环");
        request.setIdempotencyKey("week7-loop-" + UUID.randomUUID());
        request.setCurrency("CNY");
        request.setChannelCode("AUTO");
        return request;
    }

    private int count(String table, String column, String value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                value
        );
    }
}
