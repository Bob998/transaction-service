package com.ganjunbao.transaction.messaging;

import java.time.OffsetDateTime;

public record TransactionEvent(
        Long eventId,
        String transactionId,
        String eventType,
        String payload,
        OffsetDateTime occurredAt
) {
}