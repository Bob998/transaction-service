package com.ganjunbao.transaction.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionApiIntegrationTest extends PostgresContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void shouldRejectDuplicateIdempotencyKey() throws Exception {
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

        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code")
                        .value("DUPLICATE_IDEMPOTENCY_KEY"))
                .andExpect(jsonPath("$.message").isNotEmpty());
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
}
