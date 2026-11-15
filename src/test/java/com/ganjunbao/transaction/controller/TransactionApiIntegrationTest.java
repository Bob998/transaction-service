package com.ganjunbao.transaction.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionApiIntegrationTest extends PostgresContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldCreateAndQueryTransactionThroughApi() throws Exception {
        String idempotencyKey = "api-create-" + UUID.randomUUID();

        String requestBody = """
                {
                  "amount": 128.50,
                  "description": "API集成测试",
                  "idempotencyKey": "%s",
                  "currency": "CNY",
                  "channelCode": "DEMO"
                }
                """.formatted(idempotencyKey);

        String createResponse = mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.amount").value(128.50))
                .andExpect(jsonPath("$.description").value("API集成测试"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode responseJson = objectMapper.readTree(createResponse);
        String transactionId = responseJson.get("id").asText();

        mockMvc.perform(
                        get("/api/transactions/{id}", transactionId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId))
                .andExpect(jsonPath("$.amount").value(128.50))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void shouldReturnOriginalTransactionForDuplicateIdempotencyKey() throws Exception {
        String idempotencyKey = "api-duplicate-" + UUID.randomUUID();

        String requestBody = """
                {
                  "amount": 88.00,
                  "description": "重复请求测试",
                  "idempotencyKey": "%s",
                  "currency": "CNY",
                  "channelCode": "DEMO"
                }
                """.formatted(idempotencyKey);

        String firstResponse = mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String originalTransactionId = objectMapper
                .readTree(firstResponse)
                .get("id")
                .asText();

        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(originalTransactionId))
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey));
    }

    @Test
    void shouldReturnOriginalTransactionAfterIdempotencyCacheEviction()
            throws Exception {
        String idempotencyKey = "api-cache-miss-" + UUID.randomUUID();
        String requestBody = """
                {
                  "amount": 99.00,
                  "description": "缓存失效后的数据库兜底测试",
                  "idempotencyKey": "%s",
                  "currency": "CNY",
                  "channelCode": "DEMO"
                }
                """.formatted(idempotencyKey);

        String firstResponse = mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String originalTransactionId = objectMapper
                .readTree(firstResponse)
                .get("id")
                .asText();

        redisTemplate.delete("idempotency:result:" + idempotencyKey);

        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(originalTransactionId))
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey));

        assertThat(redisTemplate.hasKey(
                "idempotency:result:" + idempotencyKey
        )).isTrue();
    }

    @Test
    void shouldEvictStaleIdempotencyCacheAfterStatusChange()
            throws Exception {
        String idempotencyKey = "api-cache-consistency-" + UUID.randomUUID();
        String requestBody = """
                {
                  "amount": 77.00,
                  "description": "数据库与缓存一致性测试",
                  "idempotencyKey": "%s",
                  "currency": "CNY",
                  "channelCode": "DEMO"
                }
                """.formatted(idempotencyKey);

        String createResponse = mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String transactionId = objectMapper
                .readTree(createResponse)
                .get("id")
                .asText();
        String redisKey = "idempotency:result:" + idempotencyKey;
        assertThat(redisTemplate.hasKey(redisKey)).isTrue();

        mockMvc.perform(
                        post("/api/transactions/{id}/confirm", transactionId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RISK_APPROVED"));

        assertThat(redisTemplate.hasKey(redisKey)).isFalse();

        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(transactionId))
                .andExpect(jsonPath("$.status").value("RISK_APPROVED"));

        assertThat(redisTemplate.hasKey(redisKey)).isTrue();
    }

    @Test
    void shouldReturnUnifiedErrorForInvalidRequest() throws Exception {
        String invalidRequestBody = """
                {
                  "amount": 0,
                  "description": "",
                  "idempotencyKey": "",
                  "currency": "CN",
                  "channelCode": "DEMO"
                }
                """;

        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("请求参数校验失败"))
                .andExpect(jsonPath("$.errors.amount")
                        .value("金额必须大于等于0.01"))
                .andExpect(jsonPath("$.errors.description")
                        .value("交易描述不能为空"))
                .andExpect(jsonPath("$.errors.idempotencyKey")
                        .value("幂等键不能为空"))
                .andExpect(jsonPath("$.errors.currency")
                        .value("币种必须是3位代码"));
    }

    @Test
    void shouldRejectIllegalStateTransitionThroughApi() throws Exception {
        String idempotencyKey = "api-state-" + UUID.randomUUID();

        String requestBody = """
                {
                  "amount": 168.00,
                  "description": "非法状态转换测试",
                  "idempotencyKey": "%s",
                  "currency": "CNY",
                  "channelCode": "DEMO"
                }
                """.formatted(idempotencyKey);

        String createResponse = mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String transactionId = objectMapper
                .readTree(createResponse)
                .get("id")
                .asText();

        mockMvc.perform(
                        post("/api/transactions/{id}/confirm", transactionId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RISK_APPROVED"));

        mockMvc.perform(
                        post("/api/transactions/{id}/confirm", transactionId)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code")
                        .value("INVALID_TRANSACTION_STATE"))
                .andExpect(jsonPath("$.message")
                        .value("只有 CREATED 状态的交易才能通过风控"));
    }

    @Test
    void shouldRejectEleventhRequestWithinRateLimitWindow()
            throws Exception {
        String userId = "rate-limit-" + UUID.randomUUID();

        for (int requestNumber = 1; requestNumber <= 10; requestNumber++) {
            mockMvc.perform(
                            get("/api/transactions/{id}", "not-exist")
                                    .header("X-User-Id", userId)
                    )
                    .andExpect(status().isNotFound())
                    .andExpect(header().string(
                            "X-RateLimit-Limit",
                            "10"
                    ))
                    .andExpect(header().string(
                            "X-RateLimit-Count",
                            Integer.toString(requestNumber)
                    ));
        }

        mockMvc.perform(
                        get("/api/transactions/{id}", "not-exist")
                                .header("X-User-Id", userId)
                )
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().string("X-RateLimit-Count", "11"))
                .andExpect(jsonPath("$.code")
                        .value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void shouldRejectSixthTransactionByRiskSlidingWindow()
            throws Exception {
        String userId = "risk-api-" + UUID.randomUUID();

        for (int transactionNumber = 1;
             transactionNumber <= 5;
             transactionNumber++) {
            String requestBody = """
                    {
                      "amount": 10.00,
                      "description": "API风控测试",
                      "idempotencyKey": "%s",
                      "currency": "CNY",
                      "channelCode": "DEMO"
                    }
                    """.formatted("risk-api-order-" + UUID.randomUUID());

            mockMvc.perform(
                            post("/api/transactions")
                                    .header("X-User-Id", userId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("CREATED"));
        }

        String rejectedRequestBody = """
                {
                  "amount": 10.00,
                  "description": "第六笔风控拒绝测试",
                  "idempotencyKey": "%s",
                  "currency": "CNY",
                  "channelCode": "DEMO"
                }
                """.formatted("risk-api-order-" + UUID.randomUUID());

        mockMvc.perform(
                        post("/api/transactions")
                                .header("X-User-Id", userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rejectedRequestBody)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RISK_REJECTED"))
                .andExpect(jsonPath("$.message").value(
                        "交易触发风控限制，窗口内笔数=6，累计金额=60.00"
                ));
    }
}
