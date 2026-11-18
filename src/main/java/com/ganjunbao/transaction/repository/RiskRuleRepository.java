package com.ganjunbao.transaction.repository;

import com.ganjunbao.transaction.mapper.RiskRuleMapper;
import com.ganjunbao.transaction.risk.RiskRule;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RiskRuleRepository {

    private final RiskRuleMapper riskRuleMapper;

    public RiskRuleRepository(RiskRuleMapper riskRuleMapper) {
        this.riskRuleMapper = riskRuleMapper;
    }

    public List<RiskRule> findEnabledRules() {
        return riskRuleMapper.findEnabledRules();
    }
}