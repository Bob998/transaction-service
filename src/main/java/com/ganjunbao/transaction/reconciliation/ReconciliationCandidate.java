package com.ganjunbao.transaction.reconciliation;

import java.math.BigDecimal;

public class ReconciliationCandidate {

    private String transactionNo;
    private BigDecimal amount;

    public String getTransactionNo() {
        return transactionNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
