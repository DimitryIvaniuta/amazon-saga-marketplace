# Performance validation

Run the hot-product profile after the stack is healthy:

```bash
k6 run performance/hot-product-read.js
```

The script uses an arrival-rate model so server slowdown does not silently reduce offered load. It fails when the error rate reaches 1%, p95 exceeds 150 ms, or p99 exceeds 350 ms for the repeated product and SKU reads.

Override values when required:

```bash
BASE_URL=https://marketplace.example.com \
PRODUCT_ID=<product-uuid> SKU_ID=<sku-uuid> \
k6 run performance/hot-product-read.js
```

For inventory write contention, run concurrent checkouts for one SKU and inspect:

- `marketplace:inventory_reservation_duration_seconds:p95`
- `marketplace:inventory_reservation_duration_seconds:p99`
- `marketplace:inventory_contention_ratio:5m`
- `GET /api/admin/inventory/hot-skus?limit=20`
