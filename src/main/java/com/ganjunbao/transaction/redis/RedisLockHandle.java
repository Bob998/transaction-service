package com.ganjunbao.transaction.redis;

public record RedisLockHandle(
        String key,
        String token
) {
}
