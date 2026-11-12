CREATE TABLE payment_transaction
(
    id              BIGSERIAL PRIMARY KEY,
    transaction_no  VARCHAR(64)    NOT NULL,
    idempotency_key VARCHAR(100)   NOT NULL,
    channel_id      BIGINT,
    amount          NUMERIC(19, 4) NOT NULL,
    currency        CHAR(3)        NOT NULL,
    description     VARCHAR(200)   NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payment_transaction_no
        UNIQUE (transaction_no),

    CONSTRAINT uk_payment_transaction_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT fk_payment_transaction_channel
        FOREIGN KEY (channel_id)
            REFERENCES payment_channel (id),

    CONSTRAINT ck_payment_transaction_amount
        CHECK (amount > 0),

    CONSTRAINT ck_payment_transaction_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_payment_transaction_status_created_at
    ON payment_transaction (status, created_at DESC);

CREATE INDEX idx_payment_transaction_channel_created_at
    ON payment_transaction (channel_id, created_at DESC);