CREATE TABLE customer_order (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(64) NOT NULL,
    total_minor BIGINT NOT NULL CHECK (total_minor >= 0),
    currency VARCHAR(3) NOT NULL,
    payment_token VARCHAR(256),
    shipping_address JSONB NOT NULL,
    failure_code VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE order_line (
    order_id UUID NOT NULL REFERENCES customer_order(id) ON DELETE CASCADE,
    sku_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_minor BIGINT NOT NULL CHECK (unit_price_minor >= 0),
    currency VARCHAR(3) NOT NULL,
    PRIMARY KEY (order_id, sku_id)
);
CREATE TABLE order_saga (
    order_id UUID PRIMARY KEY REFERENCES customer_order(id) ON DELETE CASCADE,
    state VARCHAR(64) NOT NULL,
    inventory_compensated BOOLEAN NOT NULL,
    payment_compensated BOOLEAN NOT NULL,
    version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE checkout_idempotency (
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, idempotency_key)
);
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_id VARCHAR(128) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL,
    claimed_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_order_user_created ON customer_order(user_id, created_at DESC);
CREATE INDEX idx_order_outbox_poll ON outbox_event(status, created_at);
CREATE TABLE inbox_event (
    event_id UUID NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, consumer_name)
);
