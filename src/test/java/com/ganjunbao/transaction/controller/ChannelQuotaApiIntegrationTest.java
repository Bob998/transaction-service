package com.ganjunbao.transaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.Locale;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "transaction.channel-quota.daily-limit=100.00")
@AutoConfigureMockMvc
class ChannelQuotaApiIntegrationTest extends PostgresContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRejectTransactionWhenChannelDailyQuotaIsExhausted()
            throws Exception {
        String channelCode = ("QUOTA-" + UUID.randomUUID())
                .toUpperCase(Locale.ROOT);
        jdbcTemplate.update(
                """
                INSERT INTO payment_channel (
                    channel_code,
                    channel_name,
                    status,
                    health_status,
                    priority,
                    fixed_cost,
                    cost_rate,
                    success_rate,
                    supported_currency
                )
                VALUES (?, ?, 'ACTIVE', 'HEALTHY', 0, 0, 0, 1, 'QTA')
                """,
                channelCode,
                "API额度测试通道"
        );

        String firstIdempotencyKey = "quota-api-first-" + UUID.randomUUID();
        String firstRequest = requestBody(
                channelCode,
                firstIdempotencyKey,
                "60.00"
        );

        String firstResponse = mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(firstRequest)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstTransactionId = objectMapper
                .readTree(firstResponse)
                .get("id")
                .asText();

        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(firstRequest)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstTransactionId));

        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(
                                        channelCode,
                                        "quota-api-second-" + UUID.randomUUID(),
                                        "40.00"
                                ))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(
                                        channelCode,
                                        "quota-api-rejected-" + UUID.randomUUID(),
                                        "0.01"
                                ))
                )
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code")
                        .value("CHANNEL_QUOTA_EXCEEDED"));
    }

    private String requestBody(
            String channelCode,
            String idempotencyKey,
            String amount
    ) {
        return """
                {
                  "amount": %s,
                  "description": "API通道额度测试",
                  "idempotencyKey": "%s",
                  "currency": "QTA",
                  "channelCode": "%s"
                }
                """.formatted(amount, idempotencyKey, channelCode);
    }
}
