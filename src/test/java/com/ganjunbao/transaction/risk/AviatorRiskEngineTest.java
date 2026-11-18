package com.ganjunbao.transaction.risk;

import com.ganjunbao.transaction.repository.RiskRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AviatorRiskEngineTest {

    private RiskRuleRepository repository;
    private AviatorRiskEngine engine;

    @BeforeEach
    void setUp() {
        repository = mock(RiskRuleRepository.class);
        engine = new AviatorRiskEngine(repository);
    }

    @Test
    void shouldPassWhenNoRuleMatches() {
        RiskRule largeAmountRule =
                rule("LARGE", "amount > 10000", RiskDecision.REVIEW);
        when(repository.findEnabledRules())
                .thenReturn(List.of(largeAmountRule));

        RiskAssessment assessment = engine.evaluate(context("100.00"));

        assertEquals(RiskDecision.PASS, assessment.decision());
        assertFalse(assessment.ruleResults().get(0).matched());
    }

    @Test
    void shouldRejectWhenRejectRuleMatches() {
        RiskRule newUserLargeRule = rule(
                "NEW_USER_LARGE",
                "userLevel == 'NEW' && amount > 3000",
                RiskDecision.REJECT
        );
        when(repository.findEnabledRules())
                .thenReturn(List.of(newUserLargeRule));

        RiskAssessment assessment = engine.evaluate(context("5000.00"));

        assertEquals(RiskDecision.REJECT, assessment.decision());
        assertTrue(assessment.rejected());
    }

    @Test
    void shouldChooseRejectOverReview() {
        RiskRule reviewRule =
                rule("REVIEW", "amount > 1000", RiskDecision.REVIEW);
        RiskRule rejectRule =
                rule("REJECT", "amount > 3000", RiskDecision.REJECT);
        when(repository.findEnabledRules())
                .thenReturn(List.of(reviewRule, rejectRule));

        RiskAssessment assessment = engine.evaluate(context("5000.00"));

        assertEquals(RiskDecision.REJECT, assessment.decision());
        assertEquals(2, assessment.ruleResults().size());
    }

    @Test
    void shouldFallBackToReviewWhenExpressionFails() {
        RiskRule brokenRule = rule(
                "BROKEN",
                "amount >",
                RiskDecision.REJECT
        );
        when(repository.findEnabledRules())
                .thenReturn(List.of(brokenRule));

        RiskAssessment assessment = engine.evaluate(context("100.00"));

        assertEquals(RiskDecision.REVIEW, assessment.decision());
        assertTrue(assessment.ruleResults().get(0).evaluationFailed());
    }

    private RiskRule rule(
            String code,
            String expression,
            RiskDecision decision
    ) {
        RiskRule rule = mock(RiskRule.class);
        when(rule.getRuleCode()).thenReturn(code);
        when(rule.getVersion()).thenReturn(1);
        when(rule.getExpression()).thenReturn(expression);
        when(rule.getDecision()).thenReturn(decision);
        return rule;
    }

    private RiskContext context(String amount) {
        return new RiskContext(
                "idem-1",
                "user-1",
                "NEW",
                new BigDecimal(amount),
                "CNY",
                BigDecimal.ZERO,
                new BigDecimal("20000.00"),
                0,
                0
        );
    }
}
