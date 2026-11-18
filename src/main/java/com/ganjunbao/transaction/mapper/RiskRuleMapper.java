package com.ganjunbao.transaction.mapper;

import com.ganjunbao.transaction.risk.RiskRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RiskRuleMapper {

    @Select("""
            SELECT
                id,
                rule_code,
                rule_name,
                expression,
                decision,
                priority,
                enabled,
                version,
                description,
                created_at,
                updated_at
            FROM risk_rule
            WHERE enabled = TRUE
            ORDER BY priority ASC, id ASC
            """)
    List<RiskRule> findEnabledRules();
}