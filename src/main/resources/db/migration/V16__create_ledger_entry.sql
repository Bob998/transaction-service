CREATE TABLE ledger_entry
(
    id             BIGSERIAL PRIMARY KEY,
    entry_no       VARCHAR(64)    NOT NULL,
    transaction_id BIGINT         NOT NULL,
    user_id        VARCHAR(100),
    entry_type     VARCHAR(30)    NOT NULL,
    direction      VARCHAR(10)    NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    currency       CHAR(3)        NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_ledger_entry_no UNIQUE (entry_no),
    CONSTRAINT uk_ledger_transaction_type
        UNIQUE (transaction_id, entry_type),
    CONSTRAINT fk_ledger_entry_transaction
        FOREIGN KEY (transaction_id)
            REFERENCES payment_transaction (id),
    CONSTRAINT ck_ledger_entry_type
        CHECK (entry_type IN ('PAYMENT')),
    CONSTRAINT ck_ledger_entry_direction
        CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entry_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_ledger_entry_user_created_at
    ON ledger_entry (user_id, created_at DESC);

CREATE INDEX idx_ledger_entry_transaction
    ON ledger_entry (transaction_id);
