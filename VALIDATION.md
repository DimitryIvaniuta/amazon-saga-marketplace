# Validation report

Validation date: **2026-06-19**

## Completed checks

- Repository structure: 11 Gradle modules and 129 Java source files.
- Static repository verifier: passed.
- YAML parsing: Docker Compose, Prometheus/Grafana provisioning, alert rules, and every Spring application configuration parsed successfully.
- JSON parsing: Postman collection/environment, Grafana dashboard, and repository metadata parsed successfully.
- Java parser scan: all 24 changed/new Java sources passed syntax parsing; dependency-resolution diagnostics were excluded because the runner lacks the project toolchain and artifacts.
- JavaScript syntax: k6 hot-product workload passed `node --check`.
- Shell syntax: Gradle bootstrap and project scripts passed `bash -n`.
- Git whitespace/error scan: passed `git diff --check`.
- Test inventory: 21 test classes and 40 test methods.
- Added/expanded tests cover cache TTL jitter, latency histogram configuration, end-to-end Saga timing, gateway hot-key stability, no-oversell under concurrent reservation, transactional rollback, and distribution of hot-SKU writes across multiple buckets.
- Database migration review covers safe conversion of existing inventory and reservation-line allocations to striped bucket rows.
- Configuration guards verify Kafka KRaft persistence, Jackson 3 packages, explicit auto-configuration, outbox activation, issuer/audience JWT validation, required Kafka topics/migrations, p95/p99 recording rules, Redis LFU limits, and hot-product protection assets.
- Packaging verification excludes Git metadata, build outputs, caches, IDE files, and the source upload archive.

## Tail-latency and hot-product implementation reviewed

- Aggregable Prometheus histograms and p95/p99 recording rules for HTTP, outbound calls, Kafka listeners, cache fills, inventory reservations, and terminal checkout Sagas.
- Low-cardinality metric tags; SKU/product IDs are excluded from Prometheus labels.
- Caffeine near-cache, Redis shared cache, per-replica/distributed single-flight, negative caching, TTL jitter, token-owned lock release, and cache-failure fallback.
- Route-wide, per-client, and per-resource gateway token-bucket limits.
- Bounded gateway connect, response, fixed-pool, pool-acquire, Redis, database-acquire, and cache-fill queues.
- Sixteen inventory stripes per SKU with rotating selection, `LIMIT`, and `FOR UPDATE SKIP LOCKED`.
- Exact reservation allocation persistence for commit/release/restock.
- Distinct transient-contention and genuine-shortage outcomes, with a dedicated low-latency Kafka contention backoff.
- Prometheus, Grafana, Postman diagnostics, and k6 workload assets.

## Execution limitation of this build runner

The runner provides Java 21 but does not provide JDK 25, Gradle, Docker, cached Maven artifacts, or outbound dependency resolution. Therefore the full Java 25 Gradle build, Testcontainers suite, Docker image build, Prometheus rule execution, k6 load run, and end-to-end Compose smoke test could not be executed here. Static and configuration checks passed, but this report does not claim runtime execution that the environment could not perform.

## Reproduction commands on a machine with Docker and JDK 25

```bash
./gradlew clean check javadoc
python3 scripts/static-verify.py
docker compose build
docker compose --profile observability up -d
./scripts/smoke-test.sh
k6 run performance/hot-product-read.js
k6 run performance/catalog-cache-penetration.js
```

After testing:

```bash
docker compose --profile observability down -v
```
