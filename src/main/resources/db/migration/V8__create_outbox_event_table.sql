CREATE TABLE outbox_event (
                              id BIGSERIAL PRIMARY KEY,
                              aggregate_type VARCHAR(50) NOT NULL,
                              aggregate_id VARCHAR(64) NOT NULL,
                              event_type VARCHAR(100) NOT NULL,
                              payload JSONB NOT NULL,
                              status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_event_status_created_at
    ON outbox_event (status, created_at);

CREATE INDEX idx_outbox_event_aggregate
    ON outbox_event (aggregate_type, aggregate_id);