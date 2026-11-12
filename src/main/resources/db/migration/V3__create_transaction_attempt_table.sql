CREATE TABLE transaction_attempt
(
    id             BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT       NOT NULL,
    channel_id     BIGINT       NOT NULL,
    attempt_no     INTEGER      NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    request_data   JSONB,
    response_data  JSONB,
    error_code     VARCHAR(50),
    error_message  VARCHAR(500),
    started_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at    TIMESTAMPTZ,

    CONSTRAINT fk_transaction_attempt_transaction
        FOREIGN KEY (transaction_id)
            REFERENCES payment_transaction (id),

    CONSTRAINT fk_transaction_attempt_channel
        FOREIGN KEY (channel_id)
            REFERENCES payment_channel (id),

    CONSTRAINT uk_transaction_attempt_number
        UNIQUE (transaction_id, attempt_no),

    CONSTRAINT ck_transaction_attempt_number
        CHECK (attempt_no > 0),

    CONSTRAINT ck_transaction_attempt_status
        CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_transaction_attempt_transaction_started_at
    ON transaction_attempt (transaction_id, started_at DESC);

CREATE INDEX idx_transaction_attempt_channel_status
    ON transaction_attempt (channel_id, status);