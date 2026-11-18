package com.ganjunbao.transaction.risk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganjunbao.transaction.repository.RiskEvaluationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskAssessmentService {

    private final AviatorRiskEngine riskEngine;
    private final RiskEvaluationRepository evaluationRepository;
    private final ObjectMapper objectMapper;

    public RiskAssessmentService(
            AviatorRiskEngine riskEngine,
            RiskEvaluationRepository evaluationRepository,
            ObjectMapper objectMapper
    ) {
        this.riskEngine = riskEngine;
        this.evaluationRepository = evaluationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RiskAssessment evaluateAndRecord(RiskContext context) {
        RiskAssessment assessment = riskEngine.evaluate(context);
        String contextJson = serializeContext(context);

        for (RiskRuleResult result : assessment.ruleResults()) {
            evaluationRepository.record(
                    context,
                    result,
                    contextJson
            );
        }

        return assessment;
    }

    private String serializeContext(RiskContext context) {
        try {
            return objectMapper.writeValueAsString(
                    context.toEnvironment()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "风控上下文序列化失败",
                    exception
            );
        }
    }
}