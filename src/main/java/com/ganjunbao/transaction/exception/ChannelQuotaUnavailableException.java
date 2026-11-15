package com.ganjunbao.transaction.exception;

public class ChannelQuotaUnavailableException extends RuntimeException {

    public ChannelQuotaUnavailableException(Throwable cause) {
        super("通道额度服务暂时不可用，请稍后重试", cause);
    }
}
