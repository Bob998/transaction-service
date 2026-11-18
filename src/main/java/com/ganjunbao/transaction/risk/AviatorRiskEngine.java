package com.ganjunbao.transaction.risk;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.ganjunbao.transaction.repository.RiskRuleRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AviatorRiskEngine {

    private final RiskRuleRepository riskRuleRepository;

    private final ConcurrentHashMap<String, Expression>
            compiledExpressions = new ConcurrentHashMap<>();

    public AviatorRiskEngine(
            RiskRuleRepository riskRuleRepository
    ) {
        this.riskRuleRepository = riskRuleRepository;
    }

    public RiskAssessment evaluate(RiskContext context) {
        List<RiskRuleResult> results = new ArrayList<>();
        RiskDecision finalDecision = RiskDecision.PASS;

        for (RiskRule rule : riskRuleRepository.findEnabledRules()) {
            RiskRuleResult result = evaluateRule(rule, context);
            results.add(result);

            if (result.evaluationFailed()) {
                if (finalDecision == RiskDecision.PASS) {
                    finalDecision = RiskDecision.REVIEW;
                }
                continue;
            }

            if (!result.matched()) {
                continue;
            }

            if (result.decision() == RiskDecision.REJECT) {
                finalDecision = RiskDecision.REJECT;
            } else if (
                    result.decision() == RiskDecision.REVIEW
                            && finalDecision == RiskDecision.PASS
            ) {
                finalDecision = RiskDecision.REVIEW;
            }
        }

        return new RiskAssessment(
                finalDecision,
                List.copyOf(results)
        );
    }

    private RiskRuleResult evaluateRule(
            RiskRule rule,
            RiskContext context
    ) {
        try {
            Expression expression = compiledExpressions
                    .computeIfAbsent(
                            cacheKey(rule),
                            ignored -> AviatorEvaluator
                                    .getInstance()
                                    .compile(
                                            rule.getExpression(),
                                            true
                                    )
                    );

            Object value = expression.execute(
                    context.toEnvironment()
            );

            if (!(value instanceof Boolean matched)) {
                throw new IllegalStateException(
                        "风控表达式返回值不是Boolean"
                );
            }

            return new RiskRuleResult(
                    rule.getRuleCode(),
                    rule.getVersion(),
                    rule.getExpression(),
                    matched,
                    rule.getDecision(),
                    null
            );
        } catch (RuntimeException exception) {
            return new RiskRuleResult(
                    rule.getRuleCode(),
                    rule.getVersion(),
                    rule.getExpression(),
                    false,
                    rule.getDecision(),
                    exception.getMessage()
            );
        }
    }

    private String cacheKey(RiskRule rule) {
        return rule.getRuleCode()
                + ":"
                + rule.getVersion()
                + ":"
                + rule.getExpression();
    }
}