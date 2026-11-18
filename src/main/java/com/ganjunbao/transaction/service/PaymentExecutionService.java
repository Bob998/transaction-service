package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.exception.PaymentGatewayTimeoutException;
import com.ganjunbao.transaction.gateway.PaymentGatewayClient;
import com.ganjunbao.transaction.gateway.PaymentGatewayResponse;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.repository.TransactionAttemptRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentExecutionService {

    private final TransactionService transactionService;
    private final PaymentGatewayClient paymentGatewayClient;
    private final TransactionAttemptRepository transactionAttemptRepository;

    public PaymentExecutionService(
            TransactionService transactionService,
            PaymentGatewayClient paymentGatewayClient,
            TransactionAttemptRepository transactionAttemptRepository
    ) {
        this.transactionService = transactionService;
        this.paymentGatewayClient = paymentGatewayClient;
        this.transactionAttemptRepository = transactionAttemptRepository;
    }

    public PaymentExecutionOutcome executeOnce(String transactionId) {
        Transaction transaction = transactionService.getById(transactionId);

        try {
            PaymentGatewayResponse response = paymentGatewayClient.confirm(
                    transaction.getId(),
                    transaction.getAmount(),
                    transaction.getCurrency()
            );

            if ("SUCCESS".equalsIgnoreCase(response.result())) {
                transactionService.succeed(transactionId);
                return PaymentExecutionOutcome.SUCCEEDED;
            }

            transactionService.fail(transactionId);
            return PaymentExecutionOutcome.FAILED;
        } catch (PaymentGatewayTimeoutException exception) {
            // 超时不代表失败：通道可能已成功扣款，只是响应没有回来。
            // 保持交易为 PROCESSING，记录 UNKNOWN，交给查询或对账确认。
            transactionAttemptRepository.recordUnknownTimeout(transaction);
            return PaymentExecutionOutcome.UNKNOWN;
        }
    }
}
