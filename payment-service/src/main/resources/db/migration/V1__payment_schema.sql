CREATE TABLE payment (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    status VARCHAR(64) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0),
    currency VARCHAR(3) NOT NULL,
    payment_token VARCHAR(256),
    provider_payment_id UUID,
    provider_reference VARCHAR(255),
    authorization_key VARCHAR(255),
    last_error VARCHAR(2000),
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE payment_attempt (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment(id),
    operation VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(255),
    error_message VARCHAR(2000),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    UNIQUE(payment_id, operation, idempotency_key)
);
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY, aggregate_id VARCHAR(128) NOT NULL, topic VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL, payload JSONB NOT NULL, status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL, claimed_at TIMESTAMPTZ, published_at TIMESTAMPTZ,
    last_error VARCHAR(2000), created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE inbox_event (
    event_id UUID NOT NULL, consumer_name VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL, PRIMARY KEY(event_id, consumer_name)
);
CREATE INDEX idx_payment_reconciliation ON payment(status, updated_at);
CREATE INDEX idx_payment_attempt_payment ON payment_attempt(payment_id, started_at DESC);
CREATE INDEX idx_payment_outbox_poll ON outbox_event(status, created_at);
