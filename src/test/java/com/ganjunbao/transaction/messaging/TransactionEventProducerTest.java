package com.ganjunbao.transaction.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionEventProducerTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private TransactionEventProducer producer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        producer = new TransactionEventProducer(
                kafkaTemplate,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void shouldUseTransactionIdAsPartitionKey() {
        when(kafkaTemplate.send(
                eq("transaction-events"),
                eq("TXN-2001"),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(CompletableFuture.completedFuture(null));

        TransactionEvent event = new TransactionEvent(
                2001L,
                "TXN-2001",
                "TRANSACTION_CREATED",
                "{}",
                OffsetDateTime.parse("2026-11-16T10:00:00Z")
        );

        producer.send(event);

        verify(kafkaTemplate).send(
                eq("transaction-events"),
                eq("TXN-2001"),
                contains("\"eventId\":2001")
        );
    }

    @Test
    void shouldPropagateBrokerFailureToOutboxPublisher() {
        RuntimeException brokerFailure =
                new RuntimeException("broker unavailable");

        when(kafkaTemplate.send(
                eq("transaction-events"),
                eq("TXN-2002"),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(CompletableFuture.failedFuture(brokerFailure));

        TransactionEvent event = new TransactionEvent(
                2002L,
                "TXN-2002",
                "TRANSACTION_CREATED",
                "{}",
                OffsetDateTime.parse("2026-11-16T10:00:00Z")
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> producer.send(event)
        );

        assertTrue(exception.getMessage().contains("Kafka发送失败"));
        assertEquals(brokerFailure, exception.getCause());
    }
}
