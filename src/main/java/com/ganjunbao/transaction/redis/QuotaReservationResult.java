package com.ganjunbao.transaction.redis;

import java.math.BigDecimal;

public record QuotaReservationResult(
        boolean allowed,
        BigDecimal usedAmount,
        BigDecimal remainingAmount,
        BigDecimal limitAmount
) {
}
