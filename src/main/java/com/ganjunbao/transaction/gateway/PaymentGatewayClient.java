package com.ganjunbao.transaction.gateway;

import com.ganjunbao.transaction.exception.PaymentGatewayException;
import com.ganjunbao.transaction.exception.PaymentGatewayTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

@Component
public class PaymentGatewayClient {

    private final RestClient restClient;

    public PaymentGatewayClient(
            RestClient.Builder restClientBuilder,
            @Value("${payment.gateway.base-url:http://localhost:9090}")
            String baseUrl,
            @Value("${payment.gateway.connect-timeout-ms:1000}")
            int connectTimeoutMs,
            @Value("${payment.gateway.read-timeout-ms:1000}")
            int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public PaymentGatewayResponse confirm(
            String transactionId,
            BigDecimal amount,
            String currency
    ) {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                transactionId,
                amount,
                currency
        );

        try {
            return restClient.post()
                    .uri("/payments/confirm")
                    .body(request)
                    .retrieve()
                    .body(PaymentGatewayResponse.class);
        } catch (RestClientResponseException exception) {
            throw new PaymentGatewayException(
                    "支付通道返回异常状态: "
                            + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            if (isCausedByTimeout(exception)) {
                throw new PaymentGatewayTimeoutException(
                        "支付通道连接或响应超时",
                        exception
                );
            }

            throw new PaymentGatewayException(
                    "支付通道通信失败",
                    exception
            );
        }
    }

    private boolean isCausedByTimeout(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }
}
