# p95/p99 and hot-product operations

## What is measured

The shared Micrometer auto-configuration enables aggregable histogram buckets for:

- `http.server.requests`
- `http.client.requests`
- `spring.kafka.listener`
- every timer named `marketplace.*.duration`

Prometheus recording rules calculate fleet-level quantiles from buckets. Do not average percentile gauges from individual replicas. Spring Kafka creates the `spring.kafka.listener` timers automatically when Micrometer and one registry are present; the dashboard groups their bounded `application`, `name`, and `result` tags.

Primary recording rules:

```promql
marketplace:http_server_request_duration_seconds:p95
marketplace:http_server_request_duration_seconds:p99
marketplace:http_client_request_duration_seconds:p95
marketplace:http_client_request_duration_seconds:p99
marketplace:kafka_listener_duration_seconds:p95
marketplace:kafka_listener_duration_seconds:p99
marketplace:catalog_cache_load_duration_seconds:p95
marketplace:catalog_cache_load_duration_seconds:p99
marketplace:inventory_reservation_duration_seconds:p95
marketplace:inventory_reservation_duration_seconds:p99
marketplace:order_saga_duration_seconds:p95
marketplace:order_saga_duration_seconds:p99
marketplace:catalog_cache_hit_ratio:5m
marketplace:inventory_contention_ratio:5m
```

The starter alert thresholds are:

- HTTP p95 above 500 ms for 10 minutes: warning.
- HTTP p99 above 1 second for 5 minutes: critical.
- Kafka listener p99 above 2 seconds for 10 minutes: warning.
- Checkout Saga p99 above 60 seconds for 15 minutes: warning.
- Inventory contention above 5% for 5 minutes: warning.
- Catalog cache hit ratio below 80% for 10 minutes: warning.

These values are initial guardrails, not universal targets. Establish route-specific SLOs from production baselines and business criticality.

## Start the observability stack

```bash
docker compose --profile observability up --build
```

Endpoints:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Grafana dashboard: **Marketplace Latency and Hot Products**

Local Grafana defaults are `admin/admin`; override `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` outside local development.

## Catalog read path

```text
Client
  -> route-wide catalog token bucket
  -> per-client gateway token bucket
  -> per-product/SKU gateway token bucket
  -> Caffeine near-cache
  -> per-replica in-flight request coalescing
  -> Redis shared cache
  -> distributed single-flight ownership lock
  -> positive or short-lived negative cache entry
  -> PostgreSQL authoritative read
```

Important settings:

| Environment variable | Default | Purpose |
|---|---:|---|
| `CATALOG_LOCAL_CACHE_MAX_SIZE` | `10000` | Per-replica memory bound |
| `CATALOG_LOCAL_CACHE_TTL` | `20s` | Near-cache staleness bound |
| `CATALOG_REDIS_CACHE_TTL` | `5m` | Shared positive-cache lifetime |
| `CATALOG_NEGATIVE_CACHE_TTL` | `30s` | Shared authoritative-not-found lifetime |
| `CATALOG_CACHE_LOAD_TIMEOUT` | `1s` | Complete cache/read-fill deadline |
| `CATALOG_REDIS_TIMEOUT` | `250ms` | Individual Redis operation deadline |
| `CATALOG_DB_ACQUIRE_TIMEOUT` | `750ms` | Database pool queue deadline |
| `CATALOG_CACHE_LOCK_TTL` | `5s` | Dead-owner protection |
| `CATALOG_CACHE_LOCK_WAIT` | `250ms` | Bounded loser wait |
| `CATALOG_CACHE_POLL_INTERVAL` | `20ms` | Winner-result poll interval |
| `CATALOG_CACHE_TTL_JITTER` | `0.15` | Synchronized-expiry protection |
| `GATEWAY_REDIS_TIMEOUT` | `250ms` | Rate-limit store operation deadline |
| `CATALOG_ROUTE_RATE` | `5000` | Total steady catalog requests/s |
| `CATALOG_ROUTE_BURST` | `10000` | Total catalog burst tokens |
| `CATALOG_HOT_KEY_RATE` | `500` | Steady requests/s per catalog resource |
| `CATALOG_HOT_KEY_BURST` | `1000` | Burst tokens per catalog resource |

The complete cache-fill path is capped at one second, below the default 1.5-second gateway catalog timeout. A Redis error is recorded but does not fail a successful PostgreSQL response. Authoritative 404 results use a short sentinel entry in both cache tiers, preventing repeated misses for the same nonexistent identifier from penetrating to PostgreSQL.

## End-to-end checkout latency

`marketplace.order.saga.duration` measures durable order creation through a terminal Saga state (`completed`, `cancelled`, or `manual_intervention`). Prometheus derives fleet p95/p99 over a 15-minute window because checkout throughput is typically lower than HTTP request throughput. The timer is explicitly clamped from 100 ms to 10 minutes and adds business SLO buckets through 600 seconds, avoiding Micrometer's default one-minute upper bound for this long-running workflow. Product, order, and user identifiers are deliberately excluded from metric labels.

A high HTTP percentile with a normal Saga percentile points to synchronous/API pressure. A normal HTTP percentile with a high Saga percentile points to Kafka lag, participant retries, payment-provider latency, inventory contention, shipping latency, or compensation/reconciliation work. Use `marketplace:kafka_listener_duration_seconds:p95` and `:p99` to separate listener execution time from broker lag and downstream waiting.

## Inventory write path


Inventory throughput settings:

| Environment variable | Default | Purpose |
|---|---:|---|
| `INVENTORY_KAFKA_CONCURRENCY` | `6` | Concurrent command consumers per replica |
| `INVENTORY_DB_ACQUIRE_TIMEOUT` | `1s` | Inventory R2DBC pool queue bound |

Each SKU has 16 independently lockable stock rows. A reservation rotates the bucket order and locks at most `quantity` positive rows with `SKIP LOCKED`. Since each selected row has at least one available unit, selecting at most `quantity` rows is sufficient whenever the currently unlocked capacity can satisfy the request.

Exact allocations are stored in `inventory_reservation_line(bucket_index, quantity)`. This guarantees that commit/release/restock updates the same rows and preserves the stock invariant:

```text
available >= 0
reserved >= 0
sold >= 0
available + reserved + sold = logical stock total, except explicit admin restock/reset
```

The inventory consumer uses six concurrent Kafka workers by default and a one-second database-pool acquisition bound. Short-lived `InventoryContentionException` failures use a dedicated 20–100 ms exponential retry policy capped at one second, while ordinary failures retain the slower general retry policy.

When a reservation cannot allocate enough unlocked stock:

1. Aggregate available stock is checked.
2. If aggregate stock is sufficient, the result is `InventoryContentionException`; Kafka retries it.
3. If aggregate stock is insufficient, the normal compensating Saga path emits `INVENTORY_REJECTED`.

Inspect a bounded per-replica ranking without creating high-cardinality metrics:

```http
GET /api/admin/inventory/hot-skus?limit=20
Authorization: Bearer <admin-jwt>
```


### Migration rollout for existing inventory

`V3__hot_sku_inventory_buckets.sql` changes the physical stock model and backfills bucket rows. Apply it in a controlled rollout before deploying consumers that require bucket allocations. For a large production table, test lock duration and WAL volume on a production-sized copy, pause inventory writers or use a blue/green database migration plan, verify aggregate stock invariants, and only then enable the new consumers. Flyway keeps the schema transition deterministic, but it does not make an expensive backfill operationally zero-downtime by itself.

## Load validation

Run the hot catalog workload:

```bash
k6 run performance/hot-product-read.js
k6 run performance/catalog-cache-penetration.js
```

The second workload repeatedly requests a bounded pool of nonexistent UUIDs to verify negative caching and route-wide pressure protection.

Default hot-product gates:

- failed HTTP requests below 1%
- product/SKU p95 below 150 ms
- product/SKU p99 below 350 ms

Use an arrival-rate executor so a slowing server does not silently lower the offered request rate. Run tests against an isolated environment with production-like CPU, connection pools, PostgreSQL statistics, Redis topology, Kafka partitions, and realistic network latency.

## Incident playbook

### High HTTP p99, normal p95

Look for rare queueing or retries: gateway pool acquisition, PostgreSQL slow queries, JVM pauses, provider timeouts, Kafka retry/DLT events, or a single route with a much worse histogram.

### Low catalog hit ratio

Check Redis evictions, memory pressure, TTL settings, cache serialization errors, deployment churn, and whether clients are requesting a very high-cardinality working set.

### High cache lock contention

Compare database fallback p99 with lock wait. Increase capacity or tune TTL only after confirming the source. Do not simply extend lock wait until user latency becomes an unbounded queue.

### High inventory contention

Check transaction duration, bucket distribution, connection-pool saturation, database CPU/IO, and whether one SKU dominates requests. Scale consumers, reduce transaction work, or increase the bucket count through a planned migration; do not bypass reservation correctness.

### Excessive 429 responses

Confirm whether the resource is legitimately viral or abusive. Tune the global resource bucket separately from per-client limits. Protect downstream capacity before raising limits.
