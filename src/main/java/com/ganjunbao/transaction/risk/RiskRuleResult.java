package com.ganjunbao.transaction.risk;

public record RiskRuleResult(
        String ruleCode,
        int ruleVersion,
        String expression,
        boolean matched,
        RiskDecision decision,
        String errorMessage
) {

    public boolean evaluationFailed() {
        return errorMessage != null;
    }
}