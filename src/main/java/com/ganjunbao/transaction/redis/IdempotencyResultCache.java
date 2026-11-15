package com.ganjunbao.transaction.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganjunbao.transaction.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class IdempotencyResultCache {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(IdempotencyResultCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public IdempotencyResultCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${transaction.idempotency-cache.ttl-seconds:600}")
            long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<Transaction> get(String idempotencyKey) {
        try {
            String json = redisTemplate.opsForValue().get(key(idempotencyKey));
            if (json == null) {
                return Optional.empty();
            }

            CachedTransactionResult cachedResult = objectMapper.readValue(
                    json,
                    CachedTransactionResult.class
            );
            return Optional.of(toTransaction(cachedResult));
        } catch (DataAccessException | JsonProcessingException exception) {
            LOGGER.warn(
                    "读取幂等结果缓存失败，将继续访问数据库，idempotencyKey={}",
                    idempotencyKey,
                    exception
            );
            return Optional.empty();
        }
    }

    public void put(Transaction transaction) {
        CachedTransactionResult cachedResult = new CachedTransactionResult(
                transaction.getId(),
                transaction.getIdempotencyKey(),
                transaction.getChannelId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getDescription(),
                transaction.getStatus(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );

        try {
            String json = objectMapper.writeValueAsString(cachedResult);
            redisTemplate.opsForValue().set(
                    key(transaction.getIdempotencyKey()),
                    json,
                    ttl
            );
        } catch (DataAccessException | JsonProcessingException exception) {
            LOGGER.warn(
                    "写入幂等结果缓存失败，数据库结果仍然有效，idempotencyKey={}",
                    transaction.getIdempotencyKey(),
                    exception
            );
        }
    }

    public void evict(String idempotencyKey) {
        try {
            redisTemplate.delete(key(idempotencyKey));
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "删除幂等结果缓存失败，缓存过期后会自动恢复，idempotencyKey={}",
                    idempotencyKey,
                    exception
            );
        }
    }

    private Transaction toTransaction(CachedTransactionResult result) {
        return new Transaction(
                result.id(),
                result.idempotencyKey(),
                result.channelId(),
                result.amount(),
                result.currency(),
                result.description(),
                result.status(),
                result.createdAt(),
                result.updatedAt()
        );
    }

    private String key(String idempotencyKey) {
        return "idempotency:result:" + idempotencyKey;
    }
}
