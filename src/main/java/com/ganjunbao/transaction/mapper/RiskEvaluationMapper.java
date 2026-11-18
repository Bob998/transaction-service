package com.ganjunbao.transaction.mapper;

import com.ganjunbao.transaction.risk.RiskDecision;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RiskEvaluationMapper {

    @Insert("""
            INSERT INTO risk_evaluation (
                evaluation_no,
                transaction_no,
                idempotency_key,
                user_id,
                rule_code,
                rule_version,
                expression_snapshot,
                matched,
                decision,
                context_snapshot,
                evaluated_at
            )
            VALUES (
                #{evaluationNo},
                #{transactionNo},
                #{idempotencyKey},
                #{userId},
                #{ruleCode},
                #{ruleVersion},
                #{expression},
                #{matched},
                #{decision},
                CAST(#{contextJson} AS JSONB),
                CURRENT_TIMESTAMP
            )
            """)
    int insert(
            String evaluationNo,
            String transactionNo,
            String idempotencyKey,
            String userId,
            String ruleCode,
            Integer ruleVersion,
            String expression,
            Boolean matched,
            RiskDecision decision,
            String contextJson
    );
}