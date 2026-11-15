package com.ganjunbao.transaction.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserRateLimiter {

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call('INCR', KEYS[1])
                    if current == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[2])
                    end
                    if current > tonumber(ARGV[1]) then
                        return -current
                    end
                    return current
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long requestLimit;
    private final long windowMilliseconds;

    public UserRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${transaction.rate-limit.requests:10}")
            long requestLimit,
            @Value("${transaction.rate-limit.window-ms:60000}")
            long windowMilliseconds
    ) {
        this.redisTemplate = redisTemplate;
        this.requestLimit = requestLimit;
        this.windowMilliseconds = windowMilliseconds;
    }

    public RateLimitResult tryAcquire(String userId) {
        String key = "rate-limit:user:" + userId;

        Long scriptResult = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                Long.toString(requestLimit),
                Long.toString(windowMilliseconds)
        );

        if (scriptResult == null) {
            throw new IllegalStateException("Redis 限流脚本没有返回结果");
        }

        boolean allowed = scriptResult > 0;
        long currentCount = Math.abs(scriptResult);

        return new RateLimitResult(
                allowed,
                currentCount,
                requestLimit
        );
    }
}
