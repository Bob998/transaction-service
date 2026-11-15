package com.ganjunbao.transaction.redis;

import java.math.BigDecimal;

public record RiskWindowResult(
        boolean allowed,
        long transactionCount,
        BigDecimal totalAmount,
        long maxTransactionCount,
        BigDecimal maxTotalAmount
) {
}
