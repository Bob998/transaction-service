package com.ganjunbao.transaction.repository;

import com.ganjunbao.transaction.mapper.OutboxEventMapper;
import com.ganjunbao.transaction.model.OutboxEvent;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

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
}