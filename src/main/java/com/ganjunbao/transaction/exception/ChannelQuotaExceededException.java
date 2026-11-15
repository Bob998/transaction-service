package com.ganjunbao.transaction.exception;

import java.math.BigDecimal;

public class ChannelQuotaExceededException extends RuntimeException {

    public ChannelQuotaExceededException(
            String channelCode,
            BigDecimal remainingAmount
    ) {
        super("支付通道 " + channelCode
                + " 当日剩余额度不足，剩余额度=" + remainingAmount);
    }
}
