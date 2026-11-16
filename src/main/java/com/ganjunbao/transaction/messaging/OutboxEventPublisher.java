package com.ganjunbao.transaction.messaging;

import com.ganjunbao.transaction.model.OutboxEvent;
import com.ganjunbao.transaction.repository.OutboxEventRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        name = "transaction.outbox.publisher-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final TransactionEventProducer transactionEventProducer;

    public OutboxEventPublisher(
            OutboxEventRepository outboxEventRepository,
            TransactionEventProducer transactionEventProducer
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.transactionEventProducer = transactionEventProducer;
    }

    @Scheduled(fixedDelayString =
            "${transaction.outbox.publish-delay-ms:1000}")
    public void publishPendingEvents() {
        List<OutboxEvent> events =
                outboxEventRepository.claimPending(100);

        for (OutboxEvent outboxEvent : events) {
            try {
                TransactionEvent event = new TransactionEvent(
                        outboxEvent.getId(),
                        outboxEvent.getAggregateId(),
                        outboxEvent.getEventType(),
                        outboxEvent.getPayload(),
                        outboxEvent.getCreatedAt()
                );

                transactionEventProducer.send(event);
                outboxEventRepository.markPublished(outboxEvent.getId());
            } catch (RuntimeException exception) {
                outboxEventRepository.markPublishFailed(
                        outboxEvent.getId(),
                        exception.getMessage()
                );
            }
        }
    }
}
