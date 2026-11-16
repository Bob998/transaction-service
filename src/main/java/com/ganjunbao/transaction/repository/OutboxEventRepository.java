package com.ganjunbao.transaction.repository;

import com.ganjunbao.transaction.mapper.OutboxEventMapper;
import com.ganjunbao.transaction.model.OutboxEvent;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class OutboxEventRepository {

    private final OutboxEventMapper outboxEventMapper;

    public OutboxEventRepository(OutboxEventMapper outboxEventMapper) {
        this.outboxEventMapper = outboxEventMapper;
    }

    public void record(
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload
    ) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setStatus("PENDING");
        event.setCreatedAt(OffsetDateTime.now());

        outboxEventMapper.insert(event);
    }

    public int countByAggregateId(String aggregateId) {
        return outboxEventMapper.countByAggregateId(aggregateId);
    }

    public List<OutboxEvent> claimPending(int limit) {
        return outboxEventMapper.claimPending(limit);
    }

    public void markPublished(Long eventId) {
        int updatedRows = outboxEventMapper.markPublished(eventId);

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Outbox事件状态更新失败，eventId=" + eventId
            );
        }
    }

    public void markPublishFailed(
            Long eventId,
            String errorMessage
    ) {
        int updatedRows = outboxEventMapper.markPublishFailed(
                eventId,
                errorMessage
        );

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Outbox事件失败状态更新失败，eventId=" + eventId
            );
        }
    }
}