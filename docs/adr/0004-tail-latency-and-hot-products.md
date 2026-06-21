# ADR 0004: Tail latency and hot-product protection

## Status

Accepted — 2026-06-19

## Context

Average latency hides the behavior that affects real users during bursts. A marketplace can have a healthy average while a small set of viral products drives p95/p99 latency, cache stampedes, Redis hot keys, PostgreSQL row-lock queues, connection-pool exhaustion, and false out-of-stock outcomes.

Two paths have different contention characteristics:

- Catalog reads are read-heavy and tolerate bounded cache staleness.
- Inventory reservations are correctness-critical writes and must never oversell.

A single technique is therefore inappropriate for both paths.

## Decision

### Tail-latency measurement

- Publish Micrometer histogram buckets for HTTP server/client, Kafka listener, and critical `marketplace.*.duration` timers.
- Calculate p95/p99 in Prometheus with `histogram_quantile` after summing buckets across replicas.
- Keep metric labels bounded. Product and SKU identifiers are never Prometheus labels.
- Provide recording rules, alerts, a provisioned Grafana dashboard, and a k6 arrival-rate workload.

### Catalog hot reads

- Use a bounded Caffeine near-cache per catalog replica.
- Use Redis as the shared cache.
- Protect cache fills with a token-owned `SET NX` lock and compare-and-delete Lua release.
- Add randomized TTL jitter to avoid synchronized expiry.
- Bound lock waiting and fall back to PostgreSQL rather than waiting indefinitely.
- Treat Redis failures as cache misses; a successful authoritative database read is never failed by a cache outage.
- Apply both per-client and per-resource token-bucket limits at the gateway.
- Use a dedicated bounded cache Redis with `allkeys-lfu` so frequently used catalog keys are favored; keep gateway rate-limit state in a separate `noeviction`, AOF-backed Redis instance.

### Inventory hot writes

- Split each SKU into 16 stock buckets.
- Rotate the first bucket by order/SKU hash so traffic is spread across stripes.
- Select only enough positive buckets to satisfy the quantity.
- Use `FOR UPDATE SKIP LOCKED` so a reservation does not queue behind a busy bucket.
- Persist the exact per-order bucket allocation; commit, release, and restock modify those exact rows.
- Distinguish temporary contention from genuine insufficient inventory by reading aggregate availability after a zero allocation.
- Retry temporary contention through the existing Kafka retry policy; emit `INVENTORY_REJECTED` only for genuine shortage.
- Keep SKU identifiers out of Prometheus labels. A bounded per-replica diagnostic cache exposes the hottest SKU IDs through an admin endpoint.

### Queueing controls

- Bound gateway connection establishment, upstream response time, fixed-pool size, and pool-acquisition time.
- Return throttling or timeout errors under overload instead of allowing unbounded queues to inflate p99 and exhaust memory.

## Consequences

### Positive

- p95/p99 can be aggregated correctly across replicas.
- Popular catalog reads normally stop at process memory or Redis.
- Cache expiry produces one coordinated database fill instead of a thundering herd.
- One viral SKU no longer serializes every reservation on one PostgreSQL row.
- Temporary lock contention is not misreported as sold out.
- Operational dashboards identify whether latency comes from HTTP, cache fallback, outbound calls, or inventory contention.

### Trade-offs

- A catalog replica can serve data up to the near-cache TTL after a remote invalidation.
- Inventory uses more rows and stores allocation detail per reservation.
- `SKIP LOCKED` intentionally provides a contention-aware view, so callers must retry transient failures.
- The admin hot-SKU view is per replica and diagnostic, not an authoritative global ranking.
- Thresholds are starting SLOs and must be recalibrated from production traffic and capacity tests.
