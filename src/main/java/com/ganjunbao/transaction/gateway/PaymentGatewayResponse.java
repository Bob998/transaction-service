package com.ganjunbao.transaction.gateway;

public record PaymentGatewayResponse(
        String result,
        String gatewayTransactionId
) {
}
