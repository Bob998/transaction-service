UPDATE risk_rule
SET expression = 'recentTransactionCount > 5',
    updated_at = CURRENT_TIMESTAMP
WHERE rule_code = 'HIGH_FREQUENCY'
  AND version = 1;
