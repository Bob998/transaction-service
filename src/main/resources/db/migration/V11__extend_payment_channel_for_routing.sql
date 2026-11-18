ALTER TABLE payment_channel
    ADD COLUMN health_status VARCHAR(20)
        NOT NULL DEFAULT 'HEALTHY',
    ADD COLUMN priority INTEGER
        NOT NULL DEFAULT 100,
    ADD COLUMN fixed_cost NUMERIC(19, 4)
        NOT NULL DEFAULT 0,
    ADD COLUMN cost_rate NUMERIC(9, 6)
        NOT NULL DEFAULT 0,
    ADD COLUMN success_rate NUMERIC(7, 6)
        NOT NULL DEFAULT 1,
    ADD COLUMN timeout_ms INTEGER
        NOT NULL DEFAULT 3000,
    ADD COLUMN supported_currency CHAR(3)
        NOT NULL DEFAULT 'CNY',
    ADD COLUMN last_health_check_at TIMESTAMPTZ;

ALTER TABLE payment_channel
    ADD CONSTRAINT ck_payment_channel_health_status
        CHECK (
            health_status IN (
                              'HEALTHY',
                              'DEGRADED',
                              'UNHEALTHY'
                )
            ),
    ADD CONSTRAINT ck_payment_channel_priority
        CHECK (priority >= 0),
    ADD CONSTRAINT ck_payment_channel_fixed_cost
        CHECK (fixed_cost >= 0),
    ADD CONSTRAINT ck_payment_channel_cost_rate
        CHECK (cost_rate >= 0),
    ADD CONSTRAINT ck_payment_channel_success_rate
        CHECK (
            success_rate >= 0
            AND success_rate <= 1
        ),
    ADD CONSTRAINT ck_payment_channel_timeout
        CHECK (timeout_ms > 0);

CREATE INDEX idx_payment_channel_routing
    ON payment_channel (
                        status,
                        health_status,
                        supported_currency,
                        priority
        );

INSERT INTO payment_channel (
    channel_code,
    channel_name,
    status,
    health_status,
    priority,
    fixed_cost,
    cost_rate,
    success_rate,
    timeout_ms,
    supported_currency
)
VALUES
    (
        'FAST_PAY',
        '快速支付通道',
        'ACTIVE',
        'HEALTHY',
        10,
        0.30,
        0.006000,
        0.980000,
        2000,
        'CNY'
    ),
    (
        'SAFE_PAY',
        '稳定支付通道',
        'ACTIVE',
        'HEALTHY',
        20,
        0.50,
        0.004000,
        0.995000,
        3000,
        'CNY'
    ),
    (
        'BACKUP_PAY',
        '备用支付通道',
        'ACTIVE',
        'HEALTHY',
        100,
        0.80,
        0.008000,
        0.950000,
        5000,
        'CNY'
    );