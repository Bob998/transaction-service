package com.ganjunbao.transaction.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class Transaction {
    private String id;
    private BigDecimal amount;
    private String description;
    private TransactionStatus status;
    private OffsetDateTime createdAt;
    private String idempotencyKey;
    private Long channelId;
    private String currency;
    private OffsetDateTime updatedAt;
    private Long databaseId;
    private Long version = 0L;
    private String userId;

    public Transaction() {}

    public Transaction(String id, BigDecimal amount, String description, TransactionStatus status, OffsetDateTime createdAt) {
        this.id = id;
        this.amount = amount;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Transaction(
            String id,
            String idempotencyKey,
            Long channelId,
            BigDecimal amount,
            String currency,
            String description,
            TransactionStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(
                id,
                idempotencyKey,
                null,
                channelId,
                amount,
                currency,
                description,
                status,
                createdAt,
                updatedAt
        );
    }

    public Transaction(
            String id,
            String idempotencyKey,
            String userId,
            Long channelId,
            BigDecimal amount,
            String currency,
            String description,
            TransactionStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.channelId = channelId;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getChannelId() {
        return channelId;
    }

    public String getCurrency() {
        return currency;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @JsonIgnore
    public Long getDatabaseId() {
        return databaseId;
    }

    public Long getVersion() {
        return version;
    }

    public void approveRisk() {
        this.status = TransactionStatus.RISK_APPROVED;
    }

    public void startProcessing() {
        this.status = TransactionStatus.PROCESSING;
    }

    public void succeed() {
        this.status = TransactionStatus.SUCCEEDED;
    }

    public void fail() {
        this.status = TransactionStatus.FAILED;
    }

    public void cancel() {
        this.status = TransactionStatus.CANCELLED;
    }

    public String getUserId() {
        return userId;
    }
}
