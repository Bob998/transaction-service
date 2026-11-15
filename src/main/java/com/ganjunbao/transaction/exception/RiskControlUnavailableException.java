package com.ganjunbao.transaction.exception;

public class RiskControlUnavailableException extends RuntimeException {

    public RiskControlUnavailableException(Throwable cause) {
        super("风控服务暂时不可用，请稍后重试", cause);
    }
}
