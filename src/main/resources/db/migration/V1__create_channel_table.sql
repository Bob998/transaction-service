CREATE TABLE payment_channel
(
    id           BIGSERIAL PRIMARY KEY,
    channel_code VARCHAR(50)  NOT NULL,
    channel_name VARCHAR(100) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payment_channel_code UNIQUE (channel_code),
    CONSTRAINT ck_payment_channel_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);