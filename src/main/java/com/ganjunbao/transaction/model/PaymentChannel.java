package com.ganjunbao.transaction.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class PaymentChannel {

    private Long id;
    private String channelCode;
    private String channelName;
    private ChannelStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private ChannelHealthStatus healthStatus;
    private Integer priority;
    private java.math.BigDecimal fixedCost;
    private java.math.BigDecimal costRate;
    private java.math.BigDecimal successRate;
    private Integer timeoutMs;
    private String supportedCurrency;
    private OffsetDateTime lastHealthCheckAt;

    public PaymentChannel() {
    }

    public PaymentChannel(
            Long id,
            String channelCode,
            String channelName,
            ChannelStatus status
    ) {
        this.id = id;
        this.channelCode = channelCode;
        this.channelName = channelName;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public String getChannelName() {
        return channelName;
    }

    public ChannelStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public ChannelHealthStatus getHealthStatus() {
        return healthStatus;
    }

    public Integer getPriority() {
        return priority;
    }

    public BigDecimal getFixedCost() {
        return fixedCost;
    }

    public BigDecimal getCostRate() {
        return costRate;
    }

    public BigDecimal getSuccessRate() {
        return successRate;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public String getSupportedCurrency() {
        return supportedCurrency;
    }

    public OffsetDateTime getLastHealthCheckAt() {
        return lastHealthCheckAt;
    }
}
