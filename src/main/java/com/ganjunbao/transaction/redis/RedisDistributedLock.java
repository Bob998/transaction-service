package com.ganjunbao.transaction.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedisDistributedLock {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                        return redis.call('DEL', KEYS[1])
                    end
                    return 0
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<RedisLockHandle> tryLock(
            String resource,
            Duration leaseTime
    ) {
        if (leaseTime == null || leaseTime.isZero() || leaseTime.isNegative()) {
            throw new IllegalArgumentException("锁租期必须大于 0");
        }

        String key = "lock:" + resource;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                key,
                token,
                leaseTime
        );

        if (Boolean.TRUE.equals(acquired)) {
            return Optional.of(new RedisLockHandle(key, token));
        }
        return Optional.empty();
    }

    public boolean unlock(RedisLockHandle handle) {
        Long deleted = redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(handle.key()),
                handle.token()
        );
        return Long.valueOf(1L).equals(deleted);
    }
}
