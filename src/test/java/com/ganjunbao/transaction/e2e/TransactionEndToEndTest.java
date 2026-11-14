package com.ganjunbao.transaction.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.ganjunbao.transaction.support.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionEndToEndTest extends PostgresContainerTest {

    @LocalServerPort
    private int port;

    @Test
    void shouldCreateAndQueryTransactionOverRealHttp() {
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        String idempotencyKey = "e2e-" + UUID.randomUUID();

        Map<String, Object> request = Map.of(
                "amount", new BigDecimal("328.50"),
                "description", "端到端冒烟测试",
                "idempotencyKey", idempotencyKey,
                "currency", "CNY",
                "channelCode", "DEMO"
        );

        JsonNode created = client.post()
                .uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        assertNotNull(created);
        String transactionId = created.get("id").asText();
        assertTrue(transactionId.startsWith("TXN-"));
        assertEquals("CREATED", created.get("status").asText());

        JsonNode found = client.get()
                .uri("/api/transactions/{id}", transactionId)
                .retrieve()
                .body(JsonNode.class);

        assertNotNull(found);
        assertEquals(transactionId, found.get("id").asText());
        assertEquals("CREATED", found.get("status").asText());
        assertEquals(idempotencyKey, found.get("idempotencyKey").asText());
    }
}
