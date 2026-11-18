package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.exception.PaymentGatewayTimeoutException;
import com.ganjunbao.transaction.gateway.PaymentGatewayClient;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.repository.TransactionAttemptRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentExecutionServiceTest {

    @Test
    void shouldNotRetryOrMarkFailedWhenGatewayTimesOut() {
        TransactionService transactionService = mock(TransactionService.class);
        PaymentGatewayClient gatewayClient = mock(PaymentGatewayClient.class);
        TransactionAttemptRepository attemptRepository =
                mock(TransactionAttemptRepository.class);
        Transaction transaction = mock(Transaction.class);

        when(transactionService.getById("TXN-TIMEOUT"))
                .thenReturn(transaction);
        when(transaction.getId()).thenReturn("TXN-TIMEOUT");
        when(transaction.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(transaction.getCurrency()).thenReturn("CNY");
        when(gatewayClient.confirm(
                "TXN-TIMEOUT",
                new BigDecimal("100.00"),
                "CNY"
        )).thenThrow(new PaymentGatewayTimeoutException(
                "支付通道连接或响应超时",
                new RuntimeException("timeout")
        ));

        PaymentExecutionService service = new PaymentExecutionService(
                transactionService,
                gatewayClient,
                attemptRepository
        );

        PaymentExecutionOutcome outcome = service.executeOnce("TXN-TIMEOUT");

        assertEquals(PaymentExecutionOutcome.UNKNOWN, outcome);
        verify(gatewayClient, times(1)).confirm(
                "TXN-TIMEOUT",
                new BigDecimal("100.00"),
                "CNY"
        );
        verify(attemptRepository).recordUnknownTimeout(transaction);
        verify(transactionService, never()).succeed("TXN-TIMEOUT");
        verify(transactionService, never()).fail("TXN-TIMEOUT");
    }
}
