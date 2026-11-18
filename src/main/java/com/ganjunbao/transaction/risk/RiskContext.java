package com.ganjunbao.transaction.risk;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public record RiskContext(
        String idempotencyKey,
        String userId,
        String userLevel,
        BigDecimal amount,
        String currency,
        BigDecimal dailyAmount,
        BigDecimal dailyLimit,
        long recentTransactionCount,
        long recentFailureCount
) {

    public Map<String, Object> toEnvironment() {
        Map<String, Object> environment = new HashMap<>();

        environment.put("idempotencyKey", idempotencyKey);
        environment.put("userId", userId);
        environment.put("userLevel", userLevel);
        environment.put("amount", amount);
        environment.put("currency", currency);
        environment.put("dailyAmount", dailyAmount);
        environment.put("dailyLimit", dailyLimit);
        environment.put(
                "recentTransactionCount",
                recentTransactionCount
        );
        environment.put(
                "recentFailureCount",
                recentFailureCount
        );

        return environment;
    }
}