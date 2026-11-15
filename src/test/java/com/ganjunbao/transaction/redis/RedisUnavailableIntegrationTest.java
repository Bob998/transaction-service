package com.ganjunbao.transaction.redis;

import com.ganjunbao.transaction.repository.PaymentChannelRepository;
import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RedisUnavailableIntegrationTest extends PostgresContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentChannelRepository paymentChannelRepository;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void simulateRedisUnavailable() {
        RedisConnectionFailureException failure =
                new RedisConnectionFailureException("模拟 Redis 不可用");
        when(redisTemplate.opsForValue()).thenThrow(failure);
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenThrow(failure);
    }

    @Test
    void shouldRejectTransactionWhenQuotaCannotBeVerified()
            throws Exception {
        String idempotencyKey = "redis-down-" + UUID.randomUUID();
        String requestBody = """
                {
                  "amount": 58.00,
                  "description": "Redis不可用降级测试",
                  "idempotencyKey": "%s",
                  "currency": "CNY",
                  "channelCode": "DEMO"
                }
                """.formatted(idempotencyKey);

        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("CHANNEL_QUOTA_UNAVAILABLE"));
    }

    @Test
    void shouldFallBackToDatabaseWhenRouteCacheIsUnavailable() {
        assertThat(paymentChannelRepository.findActiveByCode("DEMO"))
                .isPresent();
    }
}
