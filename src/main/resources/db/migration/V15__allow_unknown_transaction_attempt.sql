ALTER TABLE transaction_attempt
    DROP CONSTRAINT ck_transaction_attempt_status;

ALTER TABLE transaction_attempt
    ADD CONSTRAINT ck_transaction_attempt_status
        CHECK (status IN (
            'PROCESSING',
            'SUCCEEDED',
            'FAILED',
            'UNKNOWN'
        ));
