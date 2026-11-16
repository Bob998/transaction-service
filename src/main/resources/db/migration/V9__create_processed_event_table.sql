CREATE TABLE processed_event
(
    id           BIGSERIAL PRIMARY KEY,
    event_id     BIGINT       NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_processed_event
        UNIQUE (event_id, consumer_group)
);

CREATE INDEX idx_processed_event_processed_at
    ON processed_event (processed_at);