CREATE TABLE user_risk_profile
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       VARCHAR(100)   NOT NULL,
    user_level    VARCHAR(20)    NOT NULL DEFAULT 'NEW',
    daily_limit   NUMERIC(19, 4) NOT NULL,
    status        VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_user_risk_profile_user
        UNIQUE (user_id),

    CONSTRAINT ck_user_risk_profile_level
        CHECK (
            user_level IN (
                           'NEW',
                           'NORMAL',
                           'VIP'
                )
            ),

    CONSTRAINT ck_user_risk_profile_limit
        CHECK (daily_limit > 0),

    CONSTRAINT ck_user_risk_profile_status
        CHECK (
            status IN (
                       'ACTIVE',
                       'FROZEN'
                )
            )
);

ALTER TABLE payment_transaction
    ADD COLUMN user_id VARCHAR(100);

CREATE INDEX idx_payment_transaction_user_created_at
    ON payment_transaction (
                            user_id,
                            created_at DESC
        );

INSERT INTO user_risk_profile (
    user_id,
    user_level,
    daily_limit,
    status
)
VALUES
    (
        'week7-new-user',
        'NEW',
        5000.00,
        'ACTIVE'
    ),
    (
        'week7-normal-user',
        'NORMAL',
        20000.00,
        'ACTIVE'
    ),
    (
        'week7-vip-user',
        'VIP',
        100000.00,
        'ACTIVE'
    );