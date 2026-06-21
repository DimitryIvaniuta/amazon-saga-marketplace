# Technology research snapshot — 13 June 2026

The project pins the following production baseline after checking official release and compatibility material:

- Java 25 toolchain.
- Spring Boot 4.1.0 and Spring Cloud 2025.1.2.
- Gradle 9.5.1.
- Apache Kafka 4.3.0 in KRaft mode.
- PostgreSQL 18.4.
- Redis 8.6.3.
- Native Jackson 3 rather than the deprecated Jackson 2 compatibility path in Spring Boot 4.

Primary references:

- https://spring.io/projects/spring-boot
- https://spring.io/projects/spring-cloud
- https://gradle.org/releases/
- https://kafka.apache.org/downloads
- https://www.postgresql.org/docs/current/release.html
- https://docs.spring.io/spring-boot/reference/features/json.html
- https://docs.spring.io/spring-kafka/reference/kafka/exactly-once.html

The architecture intentionally does not use XA/2PC across services or the external provider. Local ACID transactions, outbox/inbox, deterministic provider idempotency, compensation, and reconciliation are the selected consistency mechanisms.

## Tail-latency and hot-product research update — 19 June 2026

The hardening pass follows these production principles from official documentation:

- Publish Micrometer percentile histograms and calculate fleet quantiles from Prometheus buckets rather than averaging per-instance percentile gauges.
- Bound gateway connection acquisition and upstream response time, and apply both caller-level and resource-level token buckets.
- Use a bounded near-cache, a shared Redis cache, TTL jitter, and per-replica request coalescing and token-owned distributed single-flight locks to prevent synchronized expiry and cache stampedes.
- Keep reconstructible cache data on a separate Redis failure domain with LFU eviction; keep rate-limit state on a non-evicting control instance.
- Split a hot SKU into independently lockable PostgreSQL rows. `FOR UPDATE ... SKIP LOCKED` avoids waiting behind already locked stripes, while `LIMIT` bounds the rows selected and locked.
- Preserve exact per-order bucket allocations so commit, release, and restock mutate the same stock rows.
- Track product/SKU rankings in a bounded diagnostic structure instead of introducing unbounded Prometheus label cardinality.

Primary references:

- https://docs.micrometer.io/micrometer/reference/concepts/histogram-quantiles.html
- https://docs.spring.io/spring-boot/reference/actuator/metrics.html
- https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/requestratelimiter-factory.html
- https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/http-timeouts-configuration.html
- https://redis.io/docs/latest/develop/use/keyspace-notifications/
- https://redis.io/docs/latest/develop/reference/eviction/
- https://www.postgresql.org/docs/current/sql-select.html
