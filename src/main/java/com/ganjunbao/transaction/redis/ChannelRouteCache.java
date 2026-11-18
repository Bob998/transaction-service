package com.ganjunbao.transaction.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganjunbao.transaction.model.PaymentChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class ChannelRouteCache {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ChannelRouteCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public ChannelRouteCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${transaction.route-cache.ttl-seconds:300}")
            long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<PaymentChannel> get(String channelCode) {
        try {
            String json = redisTemplate.opsForValue().get(key(channelCode));
            if (json == null) {
                return Optional.empty();
            }

            CachedChannelRoute cachedRoute = objectMapper.readValue(
                    json,
                    CachedChannelRoute.class
            );
            return Optional.of(new PaymentChannel(
                    cachedRoute.id(),
                    cachedRoute.channelCode(),
                    cachedRoute.channelName(),
                    cachedRoute.status()
            ));
        } catch (DataAccessException | JsonProcessingException exception) {
            LOGGER.warn(
                    "读取通道路由缓存失败，将回源数据库，channelCode={}",
                    channelCode,
                    exception
            );
            return Optional.empty();
        }
    }

    public void put(PaymentChannel channel) {
        CachedChannelRoute cachedRoute = new CachedChannelRoute(
                channel.getId(),
                channel.getChannelCode(),
                channel.getChannelName(),
                channel.getStatus()
        );

        try {
            String json = objectMapper.writeValueAsString(cachedRoute);
            redisTemplate.opsForValue().set(
                    key(channel.getChannelCode()),
                    json,
                    ttl
            );
        } catch (DataAccessException | JsonProcessingException exception) {
            LOGGER.warn(
                    "写入通道路由缓存失败，继续使用数据库结果，channelCode={}",
                    channel.getChannelCode(),
                    exception
            );
        }
    }

    public void evict(String channelCode) {
        try {
            redisTemplate.delete(key(channelCode));
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "删除通道路由缓存失败，等待缓存自然过期，channelCode={}",
                    channelCode,
                    exception
            );
        }
    }

    private String key(String channelCode) {
        return "route:channel:" + channelCode;
    }
}
