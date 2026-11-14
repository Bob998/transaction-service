package com.ganjunbao.transaction.exception;

public class PaymentGatewayTimeoutException extends PaymentGatewayException {

    public PaymentGatewayTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
