ALTER TABLE inventory
    ADD COLUMN bucket_count SMALLINT NOT NULL DEFAULT 16
        CHECK (bucket_count BETWEEN 1 AND 64);

CREATE TABLE inventory_bucket (
    sku_id UUID NOT NULL REFERENCES inventory(sku_id) ON DELETE CASCADE,
    bucket_index SMALLINT NOT NULL,
    available_quantity INTEGER NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity INTEGER NOT NULL CHECK (reserved_quantity >= 0),
    sold_quantity INTEGER NOT NULL CHECK (sold_quantity >= 0),
    version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (sku_id, bucket_index),
    CHECK (bucket_index >= 0)
);

INSERT INTO inventory_bucket(
    sku_id, bucket_index, available_quantity, reserved_quantity,
    sold_quantity, version, updated_at)
SELECT i.sku_id,
       bucket.bucket_index,
       (i.available_quantity / i.bucket_count)
           + CASE WHEN bucket.bucket_index < (i.available_quantity % i.bucket_count) THEN 1 ELSE 0 END,
       CASE WHEN bucket.bucket_index = 0 THEN i.reserved_quantity ELSE 0 END,
       CASE WHEN bucket.bucket_index = 0 THEN i.sold_quantity ELSE 0 END,
       i.version,
       i.updated_at
  FROM inventory i
 CROSS JOIN LATERAL generate_series(0, i.bucket_count - 1) AS bucket(bucket_index);

ALTER TABLE inventory_reservation_line
    ADD COLUMN bucket_index SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE inventory_reservation_line
    DROP CONSTRAINT inventory_reservation_line_pkey;
ALTER TABLE inventory_reservation_line
    ADD PRIMARY KEY (reservation_id, sku_id, bucket_index);

CREATE INDEX idx_inventory_bucket_available
    ON inventory_bucket(sku_id, bucket_index)
    WHERE available_quantity > 0;

COMMENT ON TABLE inventory_bucket IS
    'Striped stock counters. Multiple buckets prevent one popular SKU from serializing every reservation on one row.';
COMMENT ON COLUMN inventory.available_quantity IS
    'Administrative summary only after V3; transactional availability is the sum of inventory_bucket.available_quantity.';
