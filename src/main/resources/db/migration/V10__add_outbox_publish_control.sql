ALTER TABLE outbox_event
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_at TIMESTAMPTZ,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN last_error TEXT;

CREATE INDEX idx_outbox_event_publish
    ON outbox_event (
                     status,
                     next_attempt_at,
                     created_at
        );