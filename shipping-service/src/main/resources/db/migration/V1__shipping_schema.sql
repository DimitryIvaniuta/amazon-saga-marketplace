CREATE TABLE shipment (
    id UUID PRIMARY KEY, order_id UUID NOT NULL UNIQUE, user_id UUID NOT NULL,
    status VARCHAR(64) NOT NULL, tracking_number VARCHAR(64) NOT NULL UNIQUE,
    recipient VARCHAR(255) NOT NULL, address_line1 VARCHAR(255) NOT NULL,
    city VARCHAR(128) NOT NULL, postal_code VARCHAR(32) NOT NULL,
    country VARCHAR(2) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
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
CREATE INDEX idx_shipment_user ON shipment(user_id, created_at DESC);
CREATE INDEX idx_shipping_outbox_poll ON outbox_event(status, created_at);
