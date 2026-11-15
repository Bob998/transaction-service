package com.ganjunbao.transaction.redis;

import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RiskSlidingWindowServiceIntegrationTest extends PostgresContainerTest {

    @Autowired
    private RiskSlidingWindowService riskSlidingWindowService;

    @Test
    void shouldRejectSixthTransactionWithinWindow() {
        String userId = "risk-count-" + UUID.randomUUID();

        for (int transactionNumber = 1; transactionNumber <= 5; transactionNumber++) {
            RiskWindowResult result = riskSlidingWindowService.evaluateAndRecord(
                    userId,
                    new BigDecimal("10.00")
            );

            assertThat(result.allowed()).isTrue();
            assertThat(result.transactionCount()).isEqualTo(transactionNumber);
        }

        RiskWindowResult rejectedResult = riskSlidingWindowService.evaluateAndRecord(
                userId,
                new BigDecimal("10.00")
        );

        assertThat(rejectedResult.allowed()).isFalse();
        assertThat(rejectedResult.transactionCount()).isEqualTo(6);
        assertThat(rejectedResult.totalAmount()).isEqualByComparingTo("60.00");
        assertThat(rejectedResult.maxTransactionCount()).isEqualTo(5);
    }

    @Test
    void shouldRejectTransactionWhenTotalAmountExceedsLimit() {
        String userId = "risk-amount-" + UUID.randomUUID();

        RiskWindowResult firstResult = riskSlidingWindowService.evaluateAndRecord(
                userId,
                new BigDecimal("600.00")
        );
        RiskWindowResult rejectedResult = riskSlidingWindowService.evaluateAndRecord(
                userId,
                new BigDecimal("450.00")
        );

        assertThat(firstResult.allowed()).isTrue();
        assertThat(firstResult.totalAmount()).isEqualByComparingTo("600.00");

        assertThat(rejectedResult.allowed()).isFalse();
        assertThat(rejectedResult.transactionCount()).isEqualTo(2);
        assertThat(rejectedResult.totalAmount()).isEqualByComparingTo("1050.00");
        assertThat(rejectedResult.maxTotalAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void shouldCountSameIdempotencyKeyOnlyOnce() {
        String userId = "risk-idempotent-" + UUID.randomUUID();
        String idempotencyKey = "risk-order-" + UUID.randomUUID();

        RiskWindowResult firstResult = riskSlidingWindowService
                .evaluateAndRecord(
                        userId,
                        idempotencyKey,
                        new BigDecimal("600.00")
                );
        RiskWindowResult duplicateResult = riskSlidingWindowService
                .evaluateAndRecord(
                        userId,
                        idempotencyKey,
                        new BigDecimal("600.00")
                );
        RiskWindowResult secondTransaction = riskSlidingWindowService
                .evaluateAndRecord(
                        userId,
                        "risk-order-" + UUID.randomUUID(),
                        new BigDecimal("400.00")
                );

        assertThat(firstResult.allowed()).isTrue();
        assertThat(duplicateResult.allowed()).isTrue();
        assertThat(duplicateResult.transactionCount()).isEqualTo(1);
        assertThat(duplicateResult.totalAmount())
                .isEqualByComparingTo("600.00");
        assertThat(secondTransaction.allowed()).isTrue();
        assertThat(secondTransaction.transactionCount()).isEqualTo(2);
        assertThat(secondTransaction.totalAmount())
                .isEqualByComparingTo("1000.00");
    }
}
