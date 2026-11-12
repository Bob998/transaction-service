package com.ganjunbao.transaction.model;

import java.time.OffsetDateTime;

public class AuditLog {

    private Long id;
    private Long transactionId;
    private String action;
    private String operator;
    private String oldValue;
    private String newValue;
    private OffsetDateTime createdAt;

    public AuditLog() {
    }

    public AuditLog(
            Long transactionId,
            String action,
            String operator,
            String oldValue,
            String newValue,
            OffsetDateTime createdAt
    ) {
        this.transactionId = transactionId;
        this.action = action;
        this.operator = operator;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public String getAction() {
        return action;
    }

    public String getOperator() {
        return operator;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}