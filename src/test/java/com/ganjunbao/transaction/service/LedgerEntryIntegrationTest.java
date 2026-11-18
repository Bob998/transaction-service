package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.dto.CreateTransactionRequest;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.TransactionStatus;
import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class LedgerEntryIntegrationTest extends PostgresContainerTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCommitSuccessfulTransactionAndLedgerTogether() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("68.80"));
        request.setDescription("账务流水集成测试");
        request.setIdempotencyKey("ledger-" + UUID.randomUUID());
        request.setCurrency("CNY");
        request.setChannelCode("DEMO");

        Transaction created = transactionService.create(request);
        transactionService.confirm(created.getId());
        transactionService.startProcessing(created.getId());
        Transaction succeeded = transactionService.succeed(created.getId());

        assertEquals(TransactionStatus.SUCCEEDED, succeeded.getStatus());

        Map<String, Object> ledger = jdbcTemplate.queryForMap(
                """
                SELECT entry_type, direction, amount, currency
                FROM ledger_entry
                WHERE transaction_id = ?
                """,
                succeeded.getDatabaseId()
        );

        assertEquals("PAYMENT", ledger.get("entry_type"));
        assertEquals("DEBIT", ledger.get("direction"));
        assertEquals(
                0,
                new BigDecimal(ledger.get("amount").toString())
                        .compareTo(new BigDecimal("68.80"))
        );
        assertEquals("CNY", ledger.get("currency").toString().trim());
    }
}
