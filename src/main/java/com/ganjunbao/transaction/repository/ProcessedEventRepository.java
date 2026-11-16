package com.ganjunbao.transaction.repository;

import com.ganjunbao.transaction.mapper.ProcessedEventMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedEventRepository {

    private final ProcessedEventMapper processedEventMapper;

    public ProcessedEventRepository(
            ProcessedEventMapper processedEventMapper
    ) {
        this.processedEventMapper = processedEventMapper;
    }

    public boolean markIfFirstProcessing(
            Long eventId,
            String consumerGroup
    ) {
        return processedEventMapper.insertIfAbsent(
                eventId,
                consumerGroup
        ) == 1;
    }
}