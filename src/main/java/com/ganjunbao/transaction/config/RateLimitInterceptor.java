package com.ganjunbao.transaction.config;

import com.ganjunbao.transaction.exception.RateLimitExceededException;
import com.ganjunbao.transaction.redis.RateLimitResult;
import com.ganjunbao.transaction.redis.UserRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final UserRateLimiter userRateLimiter;

    public RateLimitInterceptor(UserRateLimiter userRateLimiter) {
        this.userRateLimiter = userRateLimiter;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        String userId = request.getHeader("X-User-Id");

        if (userId == null || userId.isBlank()) {
            return true;
        }

        try {
            RateLimitResult result = userRateLimiter.tryAcquire(userId);

            response.setHeader(
                    "X-RateLimit-Limit",
                    Long.toString(result.limit())
            );
            response.setHeader(
                    "X-RateLimit-Count",
                    Long.toString(result.currentCount())
            );

            if (!result.allowed()) {
                throw new RateLimitExceededException(result.limit());
            }
        } catch (DataAccessException exception) {
            // Redis 只承担限流加速。故障时降级放行，数据库约束继续兜底。
            log.warn("Redis 限流不可用，本次请求降级放行", exception);
        }

        return true;
    }
}
