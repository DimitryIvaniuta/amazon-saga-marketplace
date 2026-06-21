# Operations

## Graceful shutdown

Every service enables Spring graceful shutdown. Kafka listeners use record acknowledgment and stop accepting new records while in-flight handlers finish. State-changing responses are not acknowledged before the local transaction commits. Outbox rows remain durable when Kafka is unavailable and are retried by the next instance.

For Kubernetes, use readiness/liveness actuator probes, a termination grace period greater than the configured Spring shutdown phase, and a `preStop` delay that first removes the pod from service endpoints.

## Payment recovery

Search `payment.status` for `*_UNKNOWN` or `*_FAILED`. Never manually issue a new provider operation key. The service persists an `*_UNKNOWN` state and an independent payment-attempt record before each provider call. Query the provider only with the stored operation key/reference and let the reconciliation job create the missing domain event. Failed cancellation or refund requires operator review and leaves the order in `MANUAL_INTERVENTION`.

## Security production checklist

Replace the repository's development PKCS12 key and passwords, use a secret manager, rotate signing keys through overlapping JWKS publication, terminate TLS at every boundary, use service identities/mTLS, encrypt opaque payment tokens, apply PCI segmentation, enable Kafka ACLs/SASL, use separate PostgreSQL roles per service, and restrict actuator exposure.

## Tail latency and hot products

Start Prometheus and Grafana with:

```bash
docker compose --profile observability up --build
```

Use histogram-derived recording rules for fleet p95/p99. Never average percentile values produced independently by replicas. The dashboard and alerts are provisioned from `observability/`.

Catalog traffic is protected by a Caffeine/Redis cache, per-replica and distributed single-flight fills, short negative caching, TTL jitter, LFU eviction, bounded gateway queues, and route/client/resource rate limits. Inventory traffic is protected by striped stock rows and `FOR UPDATE SKIP LOCKED`; transient contention is retried and is not converted into a false out-of-stock response.

See [`performance-and-hot-products.md`](performance-and-hot-products.md) for tuning values and incident procedures.
