package com.ganjunbao.transaction.gateway;

import java.math.BigDecimal;

public record PaymentGatewayRequest(
        String transactionId,
        BigDecimal amount,
        String currency
) {
}
