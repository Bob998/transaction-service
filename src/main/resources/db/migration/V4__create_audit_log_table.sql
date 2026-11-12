CREATE TABLE audit_log
(
    id             BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT,
    action         VARCHAR(50)  NOT NULL,
    operator       VARCHAR(100) NOT NULL,
    old_value      JSONB,
    new_value      JSONB,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_log_transaction
        FOREIGN KEY (transaction_id)
            REFERENCES payment_transaction (id)
);

CREATE INDEX idx_audit_log_transaction_created_at
    ON audit_log (transaction_id, created_at DESC);

CREATE INDEX idx_audit_log_action_created_at
    ON audit_log (action, created_at DESC);