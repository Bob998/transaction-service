package com.ganjunbao.transaction.risk;

import java.math.BigDecimal;

public class UserRiskProfile {

    private Long id;
    private String userId;
    private String userLevel;
    private BigDecimal dailyLimit;
    private String status;

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserLevel() {
        return userLevel;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public String getStatus() {
        return status;
    }
}