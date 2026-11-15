package com.ganjunbao.transaction.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Component
public class RiskSlidingWindowService {

    private static final DefaultRedisScript<String> RISK_WINDOW_SCRIPT =
            new DefaultRedisScript<>("""
                    local expiredMembers = redis.call(
                        'ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1]
                    )
                    redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
                    if #expiredMembers > 0 then
                        redis.call('HDEL', KEYS[2], unpack(expiredMembers))
                    end

                    redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])
                    redis.call('HSET', KEYS[2], ARGV[3], ARGV[4])
                    redis.call('PEXPIRE', KEYS[1], ARGV[7])
                    redis.call('PEXPIRE', KEYS[2], ARGV[7])

                    local members = redis.call('ZRANGE', KEYS[1], 0, -1)
                    local totalAmount = 0
                    for _, member in ipairs(members) do
                        local amount = redis.call('HGET', KEYS[2], member)
                        totalAmount = totalAmount + tonumber(amount)
                    end

                    local transactionCount = #members
                    local allowed = 1
                    if transactionCount > tonumber(ARGV[5])
                       or totalAmount > tonumber(ARGV[6]) then
                        allowed = 0
                    end

                    return allowed .. ':' .. transactionCount .. ':' .. totalAmount
                    """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final long windowMilliseconds;
    private final long maxTransactionCount;
    private final long maxTotalAmountCents;

    public RiskSlidingWindowService(
            StringRedisTemplate redisTemplate,
            @Value("${transaction.risk-window.duration-ms:60000}")
            long windowMilliseconds,
            @Value("${transaction.risk-window.max-transactions:5}")
            long maxTransactionCount,
            @Value("${transaction.risk-window.max-total-amount:1000.00}")
            BigDecimal maxTotalAmount
    ) {
        this.redisTemplate = redisTemplate;
        this.windowMilliseconds = windowMilliseconds;
        this.maxTransactionCount = maxTransactionCount;
        this.maxTotalAmountCents = toCents(maxTotalAmount, "maxTotalAmount");
    }

    public RiskWindowResult evaluateAndRecord(String userId, BigDecimal amount) {
        return evaluateAndRecord(
                userId,
                UUID.randomUUID().toString(),
                amount
        );
    }

    public RiskWindowResult evaluateAndRecord(
            String userId,
            String eventId,
            BigDecimal amount
    ) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 不能为空");
        }

        long amountCents = toCents(amount, "amount");
        if (amountCents <= 0) {
            throw new IllegalArgumentException("交易金额必须大于 0");
        }

        long now = System.currentTimeMillis();
        long windowStart = now - windowMilliseconds;
        String key = "risk-window:user:" + userId;
        String amountKey = "risk-window:amount:user:" + userId;

        String scriptResult = redisTemplate.execute(
                RISK_WINDOW_SCRIPT,
                List.of(key, amountKey),
                Long.toString(windowStart),
                Long.toString(now),
                eventId,
                Long.toString(amountCents),
                Long.toString(maxTransactionCount),
                Long.toString(maxTotalAmountCents),
                Long.toString(windowMilliseconds * 2)
        );

        if (scriptResult == null) {
            throw new IllegalStateException("Redis 风控滑动窗口脚本没有返回结果");
        }

        String[] parts = scriptResult.split(":", 3);
        boolean allowed = "1".equals(parts[0]);
        long transactionCount = Long.parseLong(parts[1]);
        long totalAmountCents = Long.parseLong(parts[2]);

        return new RiskWindowResult(
                allowed,
                transactionCount,
                fromCents(totalAmountCents),
                maxTransactionCount,
                fromCents(maxTotalAmountCents)
        );
    }

    private static long toCents(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(fieldName + " 最多只能有两位小数", exception);
        }
    }

    private static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
