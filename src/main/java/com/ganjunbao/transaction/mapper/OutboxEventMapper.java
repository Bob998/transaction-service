package com.ganjunbao.transaction.mapper;

import com.ganjunbao.transaction.model.OutboxEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

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

    @Select("""
        WITH candidates AS (
            SELECT id
            FROM outbox_event
            WHERE (
                    status = 'PENDING'
                    AND next_attempt_at <= CURRENT_TIMESTAMP
                  )
               OR (
                    status = 'PROCESSING'
                    AND locked_at <
                        CURRENT_TIMESTAMP - INTERVAL '3 minute'
                  )
            ORDER BY id
            FOR UPDATE SKIP LOCKED
            LIMIT #{limit}
        )
        UPDATE outbox_event AS event
        SET status = 'PROCESSING',
            locked_at = CURRENT_TIMESTAMP,
            attempt_count = attempt_count + 1
        FROM candidates
        WHERE event.id = candidates.id
        RETURNING
            event.id,
            event.aggregate_type,
            event.aggregate_id,
            event.event_type,
            event.payload::text AS payload,
            event.status,
            event.created_at,
            event.published_at
        """)
    List<OutboxEvent> claimPending(int limit);

    @Update("""
        UPDATE outbox_event
        SET status = 'PUBLISHED',
            published_at = CURRENT_TIMESTAMP,
            locked_at = NULL,
            last_error = NULL
        WHERE id = #{eventId}
          AND status = 'PROCESSING'
        """)
    int markPublished(Long eventId);

    @Update("""
        UPDATE outbox_event
        SET status = 'PENDING',
            locked_at = NULL,
            next_attempt_at =
                CURRENT_TIMESTAMP + INTERVAL '10 seconds',
            last_error = #{errorMessage}
        WHERE id = #{eventId}
          AND status = 'PROCESSING'
        """)
    int markPublishFailed(
            Long eventId,
            String errorMessage
    );
}