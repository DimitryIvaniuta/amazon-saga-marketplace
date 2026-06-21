CREATE TABLE audit_entry (
    id UUID PRIMARY KEY, event_id UUID NOT NULL UNIQUE, source VARCHAR(128) NOT NULL,
    action VARCHAR(255) NOT NULL, aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL, outcome VARCHAR(64) NOT NULL,
    details VARCHAR(4000) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_aggregate ON audit_entry(aggregate_id, occurred_at);
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
CREATE INDEX idx_audit_outbox_poll ON outbox_event(status, created_at);
