# Amazon Saga Marketplace

Production-oriented reactive marketplace microservices demonstrating inventory reservations, arbitrary SKU attributes, an orchestrated checkout Saga, transactional outbox/inbox messaging, idempotent external payments, compensation, reconciliation, shipping, independent audit logging, p95/p99 observability, and hot-product protection.

**Suggested GitHub repository:** `amazon-saga-marketplace`

**GitHub description:** Production-oriented Java 25 / Spring Boot 4 reactive marketplace with Saga checkout, p95/p99 observability, hot-product caching, striped inventory reservations, idempotent payments, compensation, and reconciliation.

## Selected 2026 stack

| Component | Version / approach |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.0 |
| Spring Cloud | 2025.1.2 |
| Gradle | 9.5.1 |
| PostgreSQL | 18.4 |
| Kafka | 4.3.0, three-node KRaft cluster |
| Redis | 8.6.3 |
| JSON | Native Jackson 3 `JsonMapper` |
| HTTP / persistence | WebFlux, Reactor, R2DBC, Flyway |
| Security | RSA-signed JWT, JWKS-based verification, issuer/audience validation, gateway plus service-level enforcement |

## Modules

| Module | Responsibility | Port |
|---|---|---:|
| `api-gateway` | Routing, JWT edge policy, Redis rate limiting | 8080 |
| `auth-service` | Registration, login, RSA JWT/JWKS | 8081 |
| `catalog-service` | Products, SKUs, arbitrary JSON attributes, price source | 8082 |
| `cart-service` | Per-user cart item CRUD | 8083 |
| `inventory-service` | Atomic stock reservation, commit, release, restock, expiry | 8084 |
| `order-service` | Checkout idempotency and Saga orchestration | 8085 |
| `payment-service` | Authorization/capture/cancel/refund, durable attempts, reconciliation | 8086 |
| `shipping-service` | Shipment creation and tracking projection | 8087 |
| `audit-service` | Independent append-only audit database | 8088 |
| `external-payment-simulator` | Persistent idempotent provider simulator | 8090 |
| `common-platform` | Event contracts, outbox/inbox, security and WebFlux error handling | — |


## Tail latency and hot-product hardening

The project now protects both hot reads and hot writes:

- Fleet-level HTTP, outbound-call, Kafka-listener, inventory, cache-fill, and end-to-end checkout Saga p95/p99 from Prometheus histogram buckets, with recording rules, alerts, and a provisioned Grafana dashboard.
- Bounded Caffeine near-cache plus Redis for catalog reads, including short negative caching for authoritative 404 results.
- Per-replica and distributed single-flight cache fills, token-safe lock release, and TTL jitter to stop cache stampedes.
- Separate Redis control/cache failure domains: `noeviction` for gateway rate limits and `allkeys-lfu` for reconstructible catalog cache data.
- Route-wide, per-client, and per-product/SKU gateway token buckets plus bounded upstream connection and response queues.
- Sixteen PostgreSQL inventory stripes per SKU, rotating allocation, `FOR UPDATE SKIP LOCKED`, and exact per-order bucket allocations.
- Separate outcomes for true shortage and transient contention, allowing Kafka retry without false sold-out events.
- Bounded admin diagnostics for hot SKU IDs without high-cardinality Prometheus labels.
- k6 arrival-rate validation with explicit p95/p99 gates.

See [ADR 0004](docs/adr/0004-tail-latency-and-hot-products.md) and the [performance operations guide](docs/performance-and-hot-products.md).

## Why this transaction design

There is no honest way to make an external payment provider participate in a PostgreSQL rollback. The implementation instead provides business atomicity through a durable state machine:

1. Cart data is not reserved. Checkout takes an immutable cart and authoritative-price snapshot.
2. The checkout idempotency key, order, lines, Saga row, and first outbox command commit atomically.
3. Inventory reserves with a conditional SQL update, preventing stock from becoming negative under concurrency.
4. Payment funds are **authorized**, not captured.
5. Inventory is committed to sold stock.
6. The authorization is captured.
7. Shipping is created.
8. Failures trigger release, restock, cancellation, or refund commands as appropriate.

Every participant uses a local transactional outbox and inbox. Kafka delivery remains at least once, while business mutations are effectively once. Payment calls use stable provider keys such as `<orderId>:capture:v1`. A network timeout is stored as `*_UNKNOWN` and reconciled; it is never treated as a definitive decline. If inventory expires while authorization is unresolved, the Saga waits for the provider outcome and cancels any late authorization before reaching a terminal state.

See [architecture](docs/architecture.md), [payment consistency ADR](docs/adr/0002-payment-consistency.md), and [operations](docs/operations.md).

## Run locally

Requirements: Docker Engine with Compose v2. The full build uses Java 25 and Gradle 9.5.1 inside the Docker build stage.

```bash
docker compose up --build
```

Start the bundled Prometheus/Grafana profile with:

```bash
docker compose --profile observability up --build
```

The first build compiles all services. PostgreSQL creates an independent database per service. The Kafka initializer creates all topics with 12 partitions, replication factor 3, and minimum in-sync replicas 2.

Local development-only administrator:

```text
admin@example.com
ChangeMe12345!
```

These credentials and the bundled PKCS12 signing key are intentionally local-only. Replace them before any deployment.

## Test

```bash
./gradlew clean check javadoc
python3 scripts/static-verify.py
./scripts/smoke-test.sh
```

The repository includes 21 test classes with 40 test methods, including PostgreSQL/Testcontainers concurrency and transactional-recovery coverage. The Gradle launchers bootstrap Gradle 9.5.1 when it is not already installed. CI repeats unit tests, Javadoc, static structural verification, and a Docker build matrix. See [`VALIDATION.md`](VALIDATION.md) for the completed checks and runner limitations.

Import both files from `postman/` and run the collection in order:

- `Amazon-Saga-Marketplace.postman_collection.json`
- `Amazon-Saga-Marketplace-Local.postman_environment.json`

## Provider simulator tokens

| Token | Behavior |
|---|---|
| `tok_success` | Complete authorization and capture |
| `tok_declined` | Definitive authorization rejection |
| `tok_capture_fail` | Authorization succeeds; capture is rejected and compensation starts |
| `tok_slow_authorize` | Provider commits authorization but delays the response, exercising unknown-outcome reconciliation |

Set shipping `addressLine1` to `FAIL_SHIPPING` to exercise refund plus inventory-restock compensation.

## Production hardening before deployment

The project contains production patterns, but local Compose is not a production platform. Before deployment, replace the development JWT key and all credentials, use a secret manager and per-service database roles, enable TLS/mTLS and Kafka SASL/ACLs, encrypt provider tokens, isolate the PCI boundary, use a managed payment provider, deploy PostgreSQL/Kafka/Redis with backups and multi-zone resilience, configure OpenTelemetry and alerting, and add provider webhooks signed with replay protection.

Do not clear a customer cart merely because checkout was accepted. A client may clear it after observing a terminal successful order, or a dedicated cart workflow can consume an `order.completed` event in a later extension.

## Repository creation

```bash
git init
git add .
git commit -m "feat: implement reactive Saga marketplace platform"
git branch -M main
gh repo create amazon-saga-marketplace \
  --description "Production-oriented Java 25 / Spring Boot 4 reactive marketplace with Saga checkout, idempotent payments, Kafka KRaft, PostgreSQL, Redis, outbox/inbox, shipping, and audit." \
  --source . --remote origin --push
```
