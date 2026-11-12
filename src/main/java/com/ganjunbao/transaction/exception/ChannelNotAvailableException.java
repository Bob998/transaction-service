package com.ganjunbao.transaction.exception;

public class ChannelNotAvailableException extends RuntimeException {

    public ChannelNotAvailableException(String channelCode) {
        super("支付通道不存在或不可用：" + channelCode);
    }
}