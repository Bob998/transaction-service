ALTER TABLE payment_transaction
DROP CONSTRAINT ck_payment_transaction_status;

UPDATE payment_transaction
SET status = CASE status
                 WHEN 'PENDING' THEN 'CREATED'
                 WHEN 'CONFIRMED' THEN 'SUCCEEDED'
                 ELSE status
    END;

ALTER TABLE payment_transaction
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payment_transaction
    ADD CONSTRAINT ck_payment_transaction_status
        CHECK (
            status IN (
                       'CREATED',
                       'RISK_APPROVED',
                       'PROCESSING',
                       'SUCCEEDED',
                       'FAILED',
                       'CANCELLED'
                )
            );