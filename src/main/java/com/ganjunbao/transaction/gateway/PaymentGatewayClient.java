package com.ganjunbao.transaction.gateway;

import com.ganjunbao.transaction.exception.PaymentGatewayException;
import com.ganjunbao.transaction.exception.PaymentGatewayTimeoutException;
import com.ganjunbao.transaction.observability.BusinessMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;

@Component
public class PaymentGatewayClient {

    private final RestClient restClient;
    private final BusinessMetrics metrics;

    public PaymentGatewayClient(
            RestClient.Builder restClientBuilder,
            @Value("${payment.gateway.base-url:http://localhost:9090}")
            String baseUrl,
            @Value("${payment.gateway.connect-timeout-ms:1000}")
            int connectTimeoutMs,
            @Value("${payment.gateway.read-timeout-ms:1000}")
            int readTimeoutMs,
            BusinessMetrics metrics
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.metrics = metrics;
    }

    public PaymentGatewayResponse confirm(
            String channelCode,
            String transactionId,
            BigDecimal amount,
            String currency
    ) {
        PaymentGatewayRequest request = new PaymentGatewayRequest(
                transactionId,
                amount,
                currency
        );

        long startedAt = System.nanoTime();
        String outcome = "success";
        try {
            return restClient.post()
                    .uri(
                            "/channels/{channelCode}/payments/confirm",
                            channelCode
                    )
                    .body(request)
                    .retrieve()
                    .body(PaymentGatewayResponse.class);
        } catch (RestClientResponseException exception) {
            outcome = "http_error";
            throw new PaymentGatewayException(
                    "支付通道返回异常状态: "
                            + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            if (isCausedByTimeout(exception)) {
                outcome = "timeout";
                throw new PaymentGatewayTimeoutException(
                        "支付通道连接或响应超时",
                        exception
                );
            }

            outcome = "connection_error";
            throw new PaymentGatewayException(
                    "支付通道通信失败",
                    exception
            );
        } finally {
            metrics.channelRequest(
                    channelCode,
                    outcome,
                    Duration.ofNanos(System.nanoTime() - startedAt)
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
