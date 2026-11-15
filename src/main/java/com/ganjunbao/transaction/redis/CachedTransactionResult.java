package com.ganjunbao.transaction.redis;

import com.ganjunbao.transaction.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CachedTransactionResult(
        String id,
        String idempotencyKey,
        Long channelId,
        BigDecimal amount,
        String currency,
        String description,
        TransactionStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
