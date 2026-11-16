package com.ganjunbao.transaction.messaging;

import com.ganjunbao.transaction.model.OutboxEvent;
import com.ganjunbao.transaction.repository.OutboxEventRepository;
import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class OutboxClaimIntegrationTest extends PostgresContainerTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private OutboxEventPublisher outboxEventPublisher;

    @Test
    void shouldNotClaimSameEventTwiceUnderConcurrency() throws Exception {
        String aggregateId = "TXN-OUTBOX-CLAIM-" + UUID.randomUUID();

        outboxEventRepository.record(
                "TRANSACTION",
                aggregateId,
                "OUTBOX_CLAIM_TEST",
                "{\"transactionId\":\"" + aggregateId + "\"}"
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<List<OutboxEvent>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return outboxEventRepository.claimPending(100);
            });

            Future<List<OutboxEvent>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return outboxEventRepository.claimPending(100);
            });

            ready.await();
            start.countDown();

            List<OutboxEvent> firstClaimed = first.get();
            List<OutboxEvent> secondClaimed = second.get();

            List<OutboxEvent> claimedTargetEvents =
                    java.util.stream.Stream.concat(
                                    firstClaimed.stream(),
                                    secondClaimed.stream()
                            )
                            .filter(event -> aggregateId.equals(
                                    event.getAggregateId()
                            ))
                            .toList();

            assertEquals(
                    1,
                    claimedTargetEvents.size(),
                    "同一条Outbox事件只能被一个领取者获得"
            );

            OutboxEvent claimed = claimedTargetEvents.get(0);

            assertEquals(aggregateId, claimed.getAggregateId());
            assertEquals("PROCESSING", claimed.getStatus());

            Integer attemptCount = jdbcTemplate.queryForObject(
                    "SELECT attempt_count FROM outbox_event "
                            + "WHERE aggregate_id = ?",
                    Integer.class,
                    aggregateId
            );

            assertEquals(1, attemptCount);
        } finally {
            executor.shutdownNow();

            jdbcTemplate.update(
                    "DELETE FROM outbox_event WHERE aggregate_id = ?",
                    aggregateId
            );
        }
    }
}
