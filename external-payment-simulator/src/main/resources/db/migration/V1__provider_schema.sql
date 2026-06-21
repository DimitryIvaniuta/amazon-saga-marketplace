CREATE TABLE provider_payment (
    id UUID PRIMARY KEY,
    merchant_order_id UUID NOT NULL,
    authorization_key VARCHAR(255) NOT NULL UNIQUE,
    amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0),
    currency VARCHAR(3) NOT NULL,
    payment_token_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    reference VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE provider_operation (
    operation_key VARCHAR(255) PRIMARY KEY,
    operation VARCHAR(32) NOT NULL,
    payment_id UUID NOT NULL REFERENCES provider_payment(id),
    status VARCHAR(64) NOT NULL,
    reference VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_provider_payment_order ON provider_payment(merchant_order_id);
