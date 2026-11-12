package com.ganjunbao.transaction.model;

import java.time.OffsetDateTime;

public class PaymentChannel {

    private Long id;
    private String channelCode;
    private String channelName;
    private ChannelStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public PaymentChannel() {
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
}