package com.ganjunbao.transaction.reconciliation;

import java.math.BigDecimal;

public class ReconciliationDifference {

    private Long id;
    private Long transactionId;
    private String transactionNo;
    private String differenceType;
    private BigDecimal localAmount;
    private BigDecimal channelAmount;
    private String status;

    public Long getId() {
        return id;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public String getTransactionNo() {
        return transactionNo;
    }

    public String getDifferenceType() {
        return differenceType;
    }

    public BigDecimal getLocalAmount() {
        return localAmount;
    }

    public BigDecimal getChannelAmount() {
        return channelAmount;
    }

    public String getStatus() {
        return status;
    }
}
