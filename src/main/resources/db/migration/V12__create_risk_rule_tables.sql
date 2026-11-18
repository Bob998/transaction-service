CREATE TABLE risk_rule
(
    id          BIGSERIAL PRIMARY KEY,
    rule_code   VARCHAR(64)  NOT NULL,
    rule_name   VARCHAR(100) NOT NULL,
    expression  TEXT         NOT NULL,
    decision    VARCHAR(20)  NOT NULL,
    priority    INTEGER      NOT NULL DEFAULT 100,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    version     INTEGER      NOT NULL DEFAULT 1,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_risk_rule_code_version
        UNIQUE (rule_code, version),

    CONSTRAINT ck_risk_rule_decision
        CHECK (decision IN ('REVIEW', 'REJECT')),

    CONSTRAINT ck_risk_rule_priority
        CHECK (priority >= 0),

    CONSTRAINT ck_risk_rule_version
        CHECK (version > 0)
);

CREATE INDEX idx_risk_rule_enabled_priority
    ON risk_rule (enabled, priority, id);

CREATE TABLE risk_evaluation
(
    id                  BIGSERIAL PRIMARY KEY,
    evaluation_no       VARCHAR(64)  NOT NULL,
    transaction_no      VARCHAR(64),
    idempotency_key     VARCHAR(100) NOT NULL,
    user_id             VARCHAR(100),
    rule_code           VARCHAR(64)  NOT NULL,
    rule_version        INTEGER      NOT NULL,
    expression_snapshot TEXT         NOT NULL,
    matched             BOOLEAN      NOT NULL,
    decision            VARCHAR(20)  NOT NULL,
    context_snapshot    JSONB        NOT NULL,
    evaluated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_risk_evaluation_no
        UNIQUE (evaluation_no),

    CONSTRAINT ck_risk_evaluation_decision
        CHECK (decision IN ('PASS', 'REVIEW', 'REJECT'))
);

CREATE INDEX idx_risk_evaluation_transaction
    ON risk_evaluation (
                        transaction_no,
                        evaluated_at DESC
        );

CREATE INDEX idx_risk_evaluation_user
    ON risk_evaluation (
                        user_id,
                        evaluated_at DESC
        );

CREATE INDEX idx_risk_evaluation_rule
    ON risk_evaluation (
                        rule_code,
                        matched,
                        evaluated_at DESC
        );

INSERT INTO risk_rule (
    rule_code,
    rule_name,
    expression,
    decision,
    priority,
    version,
    description
)
VALUES
    (
        'NEW_USER_LARGE_AMOUNT',
        '新用户大额交易',
        'userLevel == ''NEW'' && amount > 3000',
        'REJECT',
        10,
        1,
        '新用户单笔金额超过3000元时拒绝'
    ),
    (
        'DAILY_LIMIT_EXCEEDED',
        '用户日累计额度超限',
        'dailyAmount + amount > dailyLimit',
        'REJECT',
        20,
        1,
        '本次交易加入后超过用户日累计额度'
    ),
    (
        'HIGH_FREQUENCY',
        '短时间交易频率过高',
        'recentTransactionCount >= 5',
        'REJECT',
        30,
        1,
        '滑动窗口内交易次数达到5次'
    ),
    (
        'RECENT_FAILURES',
        '近期失败次数过多',
        'recentFailureCount >= 3',
        'REVIEW',
        40,
        1,
        '近期失败达到3次时进入人工审核'
    ),
    (
        'LARGE_AMOUNT_REVIEW',
        '单笔大额交易审核',
        'amount > 10000',
        'REVIEW',
        50,
        1,
        '单笔金额超过10000元时进入人工审核'
    );