package com.ganjunbao.transaction.exception;

import java.math.BigDecimal;

public class RiskRejectedException extends RuntimeException {

    public RiskRejectedException(long transactionCount, BigDecimal totalAmount) {
        super("交易触发风控限制，窗口内笔数=" + transactionCount
                + "，累计金额=" + totalAmount);
    }

    public RiskRejectedException(String message) {
        super(message);
    }
}
