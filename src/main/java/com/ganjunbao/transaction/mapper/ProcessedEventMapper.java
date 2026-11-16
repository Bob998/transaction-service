package com.ganjunbao.transaction.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProcessedEventMapper {

    @Insert("""
            INSERT INTO processed_event (
                event_id,
                consumer_group
            )
            VALUES (
                #{eventId},
                #{consumerGroup}
            )
            ON CONFLICT (event_id, consumer_group)
            DO NOTHING
            """)
    int insertIfAbsent(
            Long eventId,
            String consumerGroup
    );
}