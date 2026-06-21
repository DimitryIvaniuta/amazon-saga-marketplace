CREATE TABLE product (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    category VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE sku (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product(id),
    sku_code VARCHAR(100) NOT NULL UNIQUE,
    attributes JSONB NOT NULL,
    price_minor BIGINT NOT NULL CHECK (price_minor >= 0),
    currency VARCHAR(3) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_sku_product ON sku(product_id);
CREATE INDEX idx_product_active_category ON product(active, category);
