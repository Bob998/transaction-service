package com.ganjunbao.transaction.risk;

import java.time.OffsetDateTime;

public class RiskRule {

    private Long id;
    private String ruleCode;
    private String ruleName;
    private String expression;
    private RiskDecision decision;
    private Integer priority;
    private Boolean enabled;
    private Integer version;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getExpression() {
        return expression;
    }

    public RiskDecision getDecision() {
        return decision;
    }

    public Integer getPriority() {
        return priority;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Integer getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}