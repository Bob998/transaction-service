CREATE INDEX idx_payment_transaction_currency_created_at
    ON payment_transaction (currency, created_at DESC);