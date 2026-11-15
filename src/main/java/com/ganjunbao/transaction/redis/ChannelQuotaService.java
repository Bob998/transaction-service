package com.ganjunbao.transaction.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Component
public class ChannelQuotaService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DefaultRedisScript<String> RESERVE_QUOTA_SCRIPT =
            new DefaultRedisScript<>("""
                    local used = tonumber(redis.call('GET', KEYS[1]) or '0')
                    if redis.call('EXISTS', KEYS[2]) == 1 then
                        return '1:' .. used
                    end

                    local requested = tonumber(ARGV[1])
                    local quotaLimit = tonumber(ARGV[2])

                    if used + requested > quotaLimit then
                        return '0:' .. used
                    end

                    local newUsed = redis.call('INCRBY', KEYS[1], requested)
                    if newUsed == requested then
                        redis.call('PEXPIRE', KEYS[1], ARGV[3])
                    end
                    redis.call('SET', KEYS[2], requested, 'PX', ARGV[3])
                    return '1:' .. newUsed
                    """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final long keyTtlMilliseconds;
    private final Clock clock;

    @Autowired
    public ChannelQuotaService(
            StringRedisTemplate redisTemplate,
            @Value("${transaction.channel-quota.key-ttl-ms:172800000}")
            long keyTtlMilliseconds
    ) {
        this(redisTemplate, keyTtlMilliseconds, Clock.system(BUSINESS_ZONE));
    }

    ChannelQuotaService(StringRedisTemplate redisTemplate, long keyTtlMilliseconds, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.keyTtlMilliseconds = keyTtlMilliseconds;
        this.clock = clock;
    }

    public QuotaReservationResult tryReserve(
            String channelCode,
            BigDecimal requestedAmount,
            BigDecimal dailyLimit
    ) {
        return tryReserve(
                channelCode,
                requestedAmount,
                dailyLimit,
                UUID.randomUUID().toString()
        );
    }

    public QuotaReservationResult tryReserve(
            String channelCode,
            BigDecimal requestedAmount,
            BigDecimal dailyLimit,
            String reservationId
    ) {
        long requestedCents = toCents(requestedAmount, "requestedAmount");
        long limitCents = toCents(dailyLimit, "dailyLimit");

        if (requestedCents <= 0) {
            throw new IllegalArgumentException("申请额度必须大于 0");
        }
        if (limitCents <= 0) {
            throw new IllegalArgumentException("每日额度上限必须大于 0");
        }

        String date = LocalDate.now(clock).toString().replace("-", "");
        String key = "channel:quota:" + channelCode + ":" + date;
        String reservationKey = "channel:quota-reservation:"
                + channelCode + ":" + date + ":" + reservationId;

        String scriptResult = redisTemplate.execute(
                RESERVE_QUOTA_SCRIPT,
                List.of(key, reservationKey),
                Long.toString(requestedCents),
                Long.toString(limitCents),
                Long.toString(keyTtlMilliseconds)
        );

        if (scriptResult == null) {
            throw new IllegalStateException("Redis 通道额度脚本没有返回结果");
        }

        String[] parts = scriptResult.split(":", 2);
        boolean allowed = "1".equals(parts[0]);
        long usedCents = Long.parseLong(parts[1]);
        long remainingCents = Math.max(0, limitCents - usedCents);

        return new QuotaReservationResult(
                allowed,
                fromCents(usedCents),
                fromCents(remainingCents),
                fromCents(limitCents)
        );
    }

    private long toCents(BigDecimal amount, String fieldName) {
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

    private BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
