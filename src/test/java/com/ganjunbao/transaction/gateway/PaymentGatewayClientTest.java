package com.ganjunbao.transaction.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ganjunbao.transaction.exception.PaymentGatewayException;
import com.ganjunbao.transaction.exception.PaymentGatewayTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentGatewayClientTest {

    private WireMockServer wireMockServer;
    private PaymentGatewayClient paymentGatewayClient;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();

        paymentGatewayClient = new PaymentGatewayClient(
                RestClient.builder(),
                wireMockServer.baseUrl(),
                2000,
                2000
        );
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldParseSuccessfulGatewayResponse() {
        wireMockServer.stubFor(
                post(urlEqualTo("/payments/confirm"))
                        .willReturn(okJson("""
                                {
                                  "result": "SUCCESS",
                                  "gatewayTransactionId": "GW-001"
                                }
                                """))
        );

        PaymentGatewayResponse response = paymentGatewayClient.confirm(
                "TXN-001",
                new BigDecimal("100.00"),
                "CNY"
        );

        assertEquals("SUCCESS", response.result());
        assertEquals("GW-001", response.gatewayTransactionId());

        wireMockServer.verify(
                postRequestedFor(urlEqualTo("/payments/confirm"))
                        .withRequestBody(equalToJson("""
                                {
                                  "transactionId": "TXN-001",
                                  "amount": 100.00,
                                  "currency": "CNY"
                                }
                                """))
        );
    }

    @Test
    void shouldConvertGatewayServerErrorToBusinessException() {
        wireMockServer.stubFor(
                post(urlEqualTo("/payments/confirm"))
                        .willReturn(serverError())
        );

        PaymentGatewayException exception = assertThrows(
                PaymentGatewayException.class,
                () -> paymentGatewayClient.confirm(
                        "TXN-002",
                        new BigDecimal("200.00"),
                        "CNY"
                )
        );

        assertEquals(
                "支付通道返回异常状态: 500",
                exception.getMessage()
        );

        wireMockServer.verify(
                postRequestedFor(urlEqualTo("/payments/confirm"))
        );
    }

    @Test
    void shouldThrowTimeoutExceptionWhenGatewayIsTooSlow() {
        PaymentGatewayClient shortTimeoutClient =
                new PaymentGatewayClient(
                        RestClient.builder(),
                        wireMockServer.baseUrl(),
                        200,
                        200
                );

        wireMockServer.stubFor(
                post(urlEqualTo("/payments/confirm"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type",
                                                "application/json"
                                        )
                                        .withBody("""
                                                {
                                                  "result": "SUCCESS",
                                                  "gatewayTransactionId": "GW-SLOW"
                                                }
                                                """)
                                        .withFixedDelay(1000)
                        )
        );

        PaymentGatewayTimeoutException exception = assertThrows(
                PaymentGatewayTimeoutException.class,
                () -> shortTimeoutClient.confirm(
                        "TXN-SLOW",
                        new BigDecimal("300.00"),
                        "CNY"
                )
        );

        assertEquals(
                "支付通道连接或响应超时",
                exception.getMessage()
        );
    }
}
