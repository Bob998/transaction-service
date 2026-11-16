package com.ganjunbao.transaction.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganjunbao.transaction.repository.OutboxEventRepository;
import com.ganjunbao.transaction.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TransactionEventConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TransactionEventConsumer.class);

    private static final String CONSUMER_GROUP =
            "transaction-processing-group";

    private final OutboxEventRepository outboxEventRepository;

    private final Set<Long> failedOnceEventIds =
            ConcurrentHashMap.newKeySet();

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;

    public TransactionEventConsumer(
            ObjectMapper objectMapper,
            ProcessedEventRepository processedEventRepository,
            OutboxEventRepository outboxEventRepository
    ) {
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(
                    delay = 1000,
                    multiplier = 2.0
            ),
            retryTopicSuffix = "-retry",
            dltTopicSuffix = "-dlt"
    )

    @KafkaListener(
            topics = "transaction-events",
            groupId = CONSUMER_GROUP
    )
    @Transactional
    public void consume(String message) {
        TransactionEvent event = deserialize(message);

        boolean firstProcessing =
                processedEventRepository.markIfFirstProcessing(
                        event.eventId(),
                        CONSUMER_GROUP
                );

        if (!firstProcessing) {
            LOGGER.info(
                    "跳过重复事件，eventId={}, transactionId={}",
                    event.eventId(),
                    event.transactionId()
            );
            return;
        }

        if ("CONSUMER_CRASH_EXPERIMENT".equals(event.eventType())) {
            throw new IllegalStateException(
                    "模拟消费者处理中崩溃，eventId=" + event.eventId()
            );
        }

        if ("RETRY_ONCE_EXPERIMENT".equals(event.eventType())
                && failedOnceEventIds.add(event.eventId())) {
            LOGGER.warn(
                    "模拟事件第一次处理失败，eventId={}",
                    event.eventId()
            );

            throw new IllegalStateException(
                    "模拟第一次处理失败，eventId=" + event.eventId()
            );
        }

        if ("SLOW_CONSUMER_EXPERIMENT".equals(event.eventType())) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

                throw new IllegalStateException(
                        "慢消费实验被中断，eventId=" + event.eventId(),
                        exception
                );
            }
        }

        LOGGER.info(
                "处理交易事件，eventId={}, transactionId={}, eventType={}",
                event.eventId(),
                event.transactionId(),
                event.eventType()
        );

        // 后续在这里接入风控、通道路由、支付结果和对账业务。
        publishNextStage(event);
    }

    private void publishNextStage(TransactionEvent event) {
        String nextEventType = switch (event.eventType()) {
            case "TRANSACTION_CREATED" ->
                    "RISK_ASSESSMENT_COMPLETED";

            case "RISK_ASSESSMENT_COMPLETED" ->
                    "CHANNEL_ROUTED";

            case "CHANNEL_ROUTED" ->
                    "PAYMENT_SUCCEEDED";

            case "PAYMENT_SUCCEEDED" ->
                    "RECONCILIATION_REQUESTED";

            default -> null;
        };

        if (nextEventType == null) {
            return;
        }

        outboxEventRepository.record(
                "TRANSACTION",
                event.transactionId(),
                nextEventType,
                "{\"transactionId\":\""
                        + event.transactionId()
                        + "\",\"previousEvent\":\""
                        + event.eventType()
                        + "\"}"
        );

        LOGGER.info(
                "创建下一阶段Outbox事件，transactionId={}, {} -> {}",
                event.transactionId(),
                event.eventType(),
                nextEventType
        );
    }

    private TransactionEvent deserialize(String message) {
        try {
            return objectMapper.readValue(
                    message,
                    TransactionEvent.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "无法解析交易事件：" + message,
                    exception
            );
        }
    }

    @DltHandler
    public void handleDeadLetter(String message) {
        LOGGER.error(
                "消息多次重试后进入死信队列，message={}",
                message
        );
    }
}