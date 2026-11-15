package com.ganjunbao.transaction.redis;

import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ChannelQuotaServiceIntegrationTest extends PostgresContainerTest {

    @Autowired
    private ChannelQuotaService channelQuotaService;

    @Test
    void shouldReserveQuotaWithinDailyLimit() {
        String channelCode = "quota-success-" + UUID.randomUUID();

        QuotaReservationResult firstResult = channelQuotaService.tryReserve(
                channelCode,
                new BigDecimal("40.00"),
                new BigDecimal("100.00")
        );
        QuotaReservationResult secondResult = channelQuotaService.tryReserve(
                channelCode,
                new BigDecimal("35.00"),
                new BigDecimal("100.00")
        );

        assertThat(firstResult.allowed()).isTrue();
        assertThat(firstResult.usedAmount()).isEqualByComparingTo("40.00");
        assertThat(firstResult.remainingAmount()).isEqualByComparingTo("60.00");

        assertThat(secondResult.allowed()).isTrue();
        assertThat(secondResult.usedAmount()).isEqualByComparingTo("75.00");
        assertThat(secondResult.remainingAmount()).isEqualByComparingTo("25.00");
        assertThat(secondResult.limitAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldRejectQuotaOverLimitWithoutChangingUsedAmount() {
        String channelCode = "quota-reject-" + UUID.randomUUID();

        channelQuotaService.tryReserve(
                channelCode,
                new BigDecimal("80.00"),
                new BigDecimal("100.00")
        );

        QuotaReservationResult rejectedResult = channelQuotaService.tryReserve(
                channelCode,
                new BigDecimal("30.00"),
                new BigDecimal("100.00")
        );

        assertThat(rejectedResult.allowed()).isFalse();
        assertThat(rejectedResult.usedAmount()).isEqualByComparingTo("80.00");
        assertThat(rejectedResult.remainingAmount()).isEqualByComparingTo("20.00");

        QuotaReservationResult finalResult = channelQuotaService.tryReserve(
                channelCode,
                new BigDecimal("20.00"),
                new BigDecimal("100.00")
        );

        assertThat(finalResult.allowed()).isTrue();
        assertThat(finalResult.usedAmount()).isEqualByComparingTo("100.00");
        assertThat(finalResult.remainingAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldReserveOnlyOnceForSameIdempotencyKey() {
        String channelCode = "quota-idempotent-" + UUID.randomUUID();
        String reservationId = "order-" + UUID.randomUUID();

        QuotaReservationResult firstResult = channelQuotaService.tryReserve(
                channelCode,
                new BigDecimal("60.00"),
                new BigDecimal("100.00"),
                reservationId
        );
        QuotaReservationResult duplicateResult = channelQuotaService.tryReserve(
                channelCode,
                new BigDecimal("60.00"),
                new BigDecimal("100.00"),
                reservationId
        );
        QuotaReservationResult finalResult = channelQuotaService.tryReserve(
                channelCode,
                new BigDecimal("40.00"),
                new BigDecimal("100.00"),
                "another-" + UUID.randomUUID()
        );

        assertThat(firstResult.allowed()).isTrue();
        assertThat(duplicateResult.allowed()).isTrue();
        assertThat(duplicateResult.usedAmount()).isEqualByComparingTo("60.00");
        assertThat(finalResult.allowed()).isTrue();
        assertThat(finalResult.usedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldNeverExceedDailyLimitUnderConcurrency() throws Exception {
        String channelCode = "quota-concurrent-" + UUID.randomUUID();
        int requestCount = 20;
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);

        try {
            List<Future<QuotaReservationResult>> futures = new ArrayList<>();

            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return channelQuotaService.tryReserve(
                            channelCode,
                            new BigDecimal("10.00"),
                            new BigDecimal("100.00")
                    );
                }));
            }

            readyLatch.await();
            startLatch.countDown();

            List<QuotaReservationResult> results = new ArrayList<>();
            for (Future<QuotaReservationResult> future : futures) {
                results.add(future.get());
            }

            long allowedCount = results.stream()
                    .filter(QuotaReservationResult::allowed)
                    .count();

            assertThat(allowedCount).isEqualTo(10);
            assertThat(results).allSatisfy(result ->
                    assertThat(result.usedAmount())
                            .isLessThanOrEqualTo(new BigDecimal("100.00"))
            );

            QuotaReservationResult finalResult = channelQuotaService.tryReserve(
                    channelCode,
                    new BigDecimal("0.01"),
                    new BigDecimal("100.00")
            );

            assertThat(finalResult.allowed()).isFalse();
            assertThat(finalResult.usedAmount()).isEqualByComparingTo("100.00");
            assertThat(finalResult.remainingAmount()).isEqualByComparingTo("0.00");
        } finally {
            executor.shutdownNow();
        }
    }
}
