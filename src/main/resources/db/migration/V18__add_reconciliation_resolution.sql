ALTER TABLE reconciliation_difference
    ADD COLUMN resolved_by VARCHAR(100),
    ADD COLUMN resolution_note VARCHAR(500);

CREATE INDEX idx_reconciliation_difference_resolved_by
    ON reconciliation_difference (resolved_by, resolved_at DESC);
