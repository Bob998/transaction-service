package com.ganjunbao.transaction.repository;

import com.ganjunbao.transaction.mapper.RiskEvaluationMapper;
import com.ganjunbao.transaction.risk.RiskContext;
import com.ganjunbao.transaction.risk.RiskDecision;
import com.ganjunbao.transaction.risk.RiskRuleResult;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class RiskEvaluationRepository {

    private final RiskEvaluationMapper riskEvaluationMapper;

    public RiskEvaluationRepository(
            RiskEvaluationMapper riskEvaluationMapper
    ) {
        this.riskEvaluationMapper = riskEvaluationMapper;
    }

    public void record(
            RiskContext context,
            RiskRuleResult result,
            String contextJson
    ) {
        RiskDecision recordedDecision;

        if (result.evaluationFailed()) {
            recordedDecision = RiskDecision.REVIEW;
        } else if (result.matched()) {
            recordedDecision = result.decision();
        } else {
            recordedDecision = RiskDecision.PASS;
        }

        int insertedRows = riskEvaluationMapper.insert(
                "RISK-" + UUID.randomUUID(),
                null,
                context.idempotencyKey(),
                context.userId(),
                result.ruleCode(),
                result.ruleVersion(),
                result.expression(),
                result.matched(),
                recordedDecision,
                contextJson
        );

        if (insertedRows != 1) {
            throw new IllegalStateException(
                    "风控评估记录写入失败，ruleCode="
                            + result.ruleCode()
            );
        }
    }
}