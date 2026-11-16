package com.ganjunbao.transaction.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventProducer {

    private static final String TOPIC = "transaction-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TransactionEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void send(TransactionEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(
                    TOPIC,
                    event.transactionId(),
                    json
            ).get();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "交易事件序列化失败，eventId=" + event.eventId(),
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "等待Kafka发送结果时线程被中断，eventId=" + event.eventId(),
                    exception
            );
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException(
                    "Kafka发送失败，eventId=" + event.eventId(),
                    exception.getCause()
            );
        }
    }
}