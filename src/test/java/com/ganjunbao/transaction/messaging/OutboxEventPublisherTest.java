package com.ganjunbao.transaction.messaging;

import com.ganjunbao.transaction.model.OutboxEvent;
import com.ganjunbao.transaction.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventPublisherTest {

    private OutboxEventRepository repository;
    private TransactionEventProducer producer;
    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        producer = mock(TransactionEventProducer.class);
        publisher = new OutboxEventPublisher(repository, producer);
    }

    @Test
    void shouldMarkEventPublishedAfterBrokerAcknowledgement() {
        OutboxEvent event = event(3001L);
        when(repository.claimPending(100)).thenReturn(List.of(event));

        publisher.publishPendingEvents();

        verify(producer).send(any(TransactionEvent.class));
        verify(repository).markPublished(3001L);
        verify(repository, never()).markPublishFailed(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void shouldReturnEventToRetryWhenBrokerSendFails() {
        OutboxEvent event = event(3002L);
        when(repository.claimPending(100)).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(producer)
                .send(any(TransactionEvent.class));

        publisher.publishPendingEvents();

        verify(repository, never()).markPublished(3002L);
        verify(repository).markPublishFailed(
                3002L,
                "broker unavailable"
        );
    }

    private OutboxEvent event(Long id) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setAggregateId("TXN-" + id);
        event.setEventType("TRANSACTION_CREATED");
        event.setPayload("{}");
        event.setStatus("PROCESSING");
        event.setCreatedAt(OffsetDateTime.parse(
                "2026-11-16T10:00:00Z"
        ));
        return event;
    }
}
