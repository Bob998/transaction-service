package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.exception.PaymentGatewayTimeoutException;
import com.ganjunbao.transaction.gateway.PaymentGatewayClient;
import com.ganjunbao.transaction.gateway.PaymentGatewayResponse;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.model.PaymentChannel;
import com.ganjunbao.transaction.observability.BusinessMetrics;
import com.ganjunbao.transaction.observability.ChannelCircuitBreaker;
import com.ganjunbao.transaction.repository.PaymentChannelRepository;
import com.ganjunbao.transaction.repository.TransactionAttemptRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentExecutionService {

    private final TransactionService transactionService;
    private final PaymentGatewayClient paymentGatewayClient;
    private final TransactionAttemptRepository transactionAttemptRepository;
    private final PaymentChannelRepository paymentChannelRepository;
    private final ChannelCircuitBreaker circuitBreaker;
    private final BusinessMetrics metrics;

    public PaymentExecutionService(
            TransactionService transactionService,
            PaymentGatewayClient paymentGatewayClient,
            TransactionAttemptRepository transactionAttemptRepository,
            PaymentChannelRepository paymentChannelRepository,
            ChannelCircuitBreaker circuitBreaker,
            BusinessMetrics metrics
    ) {
        this.transactionService = transactionService;
        this.paymentGatewayClient = paymentGatewayClient;
        this.transactionAttemptRepository = transactionAttemptRepository;
        this.paymentChannelRepository = paymentChannelRepository;
        this.circuitBreaker = circuitBreaker;
        this.metrics = metrics;
    }

    public PaymentExecutionOutcome executeOnce(String transactionId) {
        Transaction transaction = transactionService.getById(transactionId);
        PaymentChannel channel = paymentChannelRepository
                .findActiveById(transaction.getChannelId())
                .orElseThrow(() -> new IllegalStateException(
                        "交易对应的支付通道不存在: "
                                + transaction.getChannelId()
                ));
        String channelCode = channel.getChannelCode();

        if (!circuitBreaker.allowRequest(channelCode)) {
            transactionAttemptRepository.recordUnknownTimeout(transaction);
            metrics.transactionCompleted(channelCode, "circuit_open");
            return PaymentExecutionOutcome.UNKNOWN;
        }

        try {
            PaymentGatewayResponse response = paymentGatewayClient.confirm(
                    channelCode,
                    transaction.getId(),
                    transaction.getAmount(),
                    transaction.getCurrency()
            );

            if ("SUCCESS".equalsIgnoreCase(response.result())) {
                circuitBreaker.recordSuccess(channelCode);
                transactionService.succeed(transactionId);
                metrics.transactionCompleted(channelCode, "succeeded");
                return PaymentExecutionOutcome.SUCCEEDED;
            }

            circuitBreaker.recordFailure(channelCode);
            transactionService.fail(transactionId);
            metrics.transactionCompleted(channelCode, "failed");
            return PaymentExecutionOutcome.FAILED;
        } catch (PaymentGatewayTimeoutException exception) {
            // 超时不代表失败：通道可能已成功扣款，只是响应没有回来。
            // 保持交易为 PROCESSING，记录 UNKNOWN，交给查询或对账确认。
            circuitBreaker.recordFailure(channelCode);
            transactionAttemptRepository.recordUnknownTimeout(transaction);
            metrics.transactionCompleted(channelCode, "timeout");
            return PaymentExecutionOutcome.UNKNOWN;
        }
    }
}
