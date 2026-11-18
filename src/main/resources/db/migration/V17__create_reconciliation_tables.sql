CREATE TABLE reconciliation_batch
(
    id                   BIGSERIAL PRIMARY KEY,
    business_date        DATE        NOT NULL,
    status               VARCHAR(20) NOT NULL,
    local_record_count   INTEGER     NOT NULL DEFAULT 0,
    channel_record_count INTEGER     NOT NULL DEFAULT 0,
    difference_count     INTEGER     NOT NULL DEFAULT 0,
    started_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at         TIMESTAMPTZ,

    CONSTRAINT ck_reconciliation_batch_status
        CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_reconciliation_batch_business_date
    ON reconciliation_batch (business_date, started_at DESC);

CREATE TABLE reconciliation_difference
(
    id               BIGSERIAL PRIMARY KEY,
    batch_id         BIGINT         NOT NULL,
    transaction_no   VARCHAR(64)    NOT NULL,
    difference_type  VARCHAR(30)    NOT NULL,
    local_amount     NUMERIC(19, 4),
    channel_amount   NUMERIC(19, 4),
    status           VARCHAR(20)    NOT NULL DEFAULT 'OPEN',
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at      TIMESTAMPTZ,

    CONSTRAINT fk_reconciliation_difference_batch
        FOREIGN KEY (batch_id)
            REFERENCES reconciliation_batch (id),
    CONSTRAINT uk_reconciliation_batch_transaction_type
        UNIQUE (batch_id, transaction_no, difference_type),
    CONSTRAINT ck_reconciliation_difference_type
        CHECK (difference_type IN (
            'CHANNEL_MISSING',
            'LOCAL_MISSING',
            'AMOUNT_MISMATCH'
        )),
    CONSTRAINT ck_reconciliation_difference_status
        CHECK (status IN ('OPEN', 'RESOLVED', 'IGNORED'))
);

CREATE INDEX idx_reconciliation_difference_status
    ON reconciliation_difference (status, created_at);
