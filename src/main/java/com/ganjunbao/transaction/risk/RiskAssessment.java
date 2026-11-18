package com.ganjunbao.transaction.risk;

import java.util.List;

public record RiskAssessment(
        RiskDecision decision,
        List<RiskRuleResult> ruleResults
) {

    public boolean rejected() {
        return decision == RiskDecision.REJECT;
    }

    public boolean requiresReview() {
        return decision == RiskDecision.REVIEW;
    }
}