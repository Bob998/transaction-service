package com.ganjunbao.transaction.mapper;

import com.ganjunbao.transaction.model.OutboxEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OutboxEventMapper {

    @Insert("""
            INSERT INTO outbox_event (
                aggregate_type,
                aggregate_id,
                event_type,
                payload,
                status,
                created_at
            )
            VALUES (
                #{aggregateType},
                #{aggregateId},
                #{eventType},
                CAST(#{payload} AS JSONB),
                #{status},
                #{createdAt}
            )
            """)
    int insert(OutboxEvent event);

    @Select("""
        SELECT COUNT(*)
        FROM outbox_event
        WHERE aggregate_id = #{aggregateId}
        """)
    int countByAggregateId(String aggregateId);
}