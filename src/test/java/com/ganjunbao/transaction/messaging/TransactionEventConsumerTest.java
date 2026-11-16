package com.ganjunbao.transaction.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganjunbao.transaction.repository.OutboxEventRepository;
import com.ganjunbao.transaction.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransactionEventConsumerTest {

    private ProcessedEventRepository processedEventRepository;
    private OutboxEventRepository outboxEventRepository;
    private TransactionEventConsumer consumer;

    @BeforeEach
    void setUp() {
        processedEventRepository = mock(ProcessedEventRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);

        consumer = new TransactionEventConsumer(
                new ObjectMapper(),
                processedEventRepository,
                outboxEventRepository
        );
    }

    @Test
    void shouldCreateNextOutboxEventWhenProcessingForFirstTime() {
        when(processedEventRepository.markIfFirstProcessing(
                1001L,
                "transaction-processing-group"
        )).thenReturn(true);

        consumer.consume("""
                {
                  "eventId": 1001,
                  "transactionId": "TXN-1001",
                  "eventType": "TRANSACTION_CREATED",
                  "payload": "{}",
                  "occurredAt": null
                }
                """);

        verify(outboxEventRepository).record(
                "TRANSACTION",
                "TXN-1001",
                "RISK_ASSESSMENT_COMPLETED",
                "{\"transactionId\":\"TXN-1001\","
                        + "\"previousEvent\":\"TRANSACTION_CREATED\"}"
        );
    }

    @Test
    void shouldSkipBusinessWhenEventWasAlreadyProcessed() {
        when(processedEventRepository.markIfFirstProcessing(
                1002L,
                "transaction-processing-group"
        )).thenReturn(false);

        consumer.consume("""
                {
                  "eventId": 1002,
                  "transactionId": "TXN-1002",
                  "eventType": "TRANSACTION_CREATED",
                  "payload": "{}",
                  "occurredAt": null
                }
                """);

        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void shouldNotCreateNextEventForFinalReconciliationStage() {
        when(processedEventRepository.markIfFirstProcessing(
                1003L,
                "transaction-processing-group"
        )).thenReturn(true);

        consumer.consume("""
                {
                  "eventId": 1003,
                  "transactionId": "TXN-1003",
                  "eventType": "RECONCILIATION_REQUESTED",
                  "payload": "{}",
                  "occurredAt": null
                }
                """);

        verify(outboxEventRepository, never()).record(
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
    }
}
