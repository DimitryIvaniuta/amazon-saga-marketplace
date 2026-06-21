CREATE TABLE inventory (
    sku_id UUID PRIMARY KEY,
    available_quantity INTEGER NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity INTEGER NOT NULL CHECK (reserved_quantity >= 0),
    sold_quantity INTEGER NOT NULL CHECK (sold_quantity >= 0),
    version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE inventory_reservation (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    status VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE inventory_reservation_line (
    reservation_id UUID NOT NULL REFERENCES inventory_reservation(id) ON DELETE CASCADE,
    sku_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (reservation_id, sku_id)
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
CREATE INDEX idx_inventory_reservation_expiry ON inventory_reservation(status, expires_at);
CREATE INDEX idx_inventory_outbox_poll ON outbox_event(status, created_at);
CREATE TABLE inbox_event (
    event_id UUID NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, consumer_name)
);
