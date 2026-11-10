package com.ganjunbao.transaction.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {
    private String id;
    private BigDecimal amount;
    private String description;
    private TransactionStatus status;
    private LocalDateTime createdAt;

    public Transaction(String id, BigDecimal amount, String description, TransactionStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.amount = amount;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void confirm() {
        this.status = TransactionStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = TransactionStatus.CANCELLED;
    }
}
