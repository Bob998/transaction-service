package com.ganjunbao.transaction.redis;

public record RateLimitResult(
        boolean allowed,
        long currentCount,
        long limit
) {
}
