# ADR 0002: Payment consistency without distributed XA

## Decision

Use provider-side idempotency, authorization before capture, durable attempts, an outbox/inbox, compensation, and scheduled reconciliation.

## Rationale

An external payment provider cannot join the PostgreSQL transaction. A timeout after a successful provider commit is an unknown outcome, not a failure. Retrying with a new key can double-charge; compensating before querying can also be wrong.

## Protocol

- Persist the attempt and command inbox claim first.
- Call the provider with `<orderId>:<operation>:v1`.
- On a definitive 4xx decline, persist failure and publish a failure event.
- On timeout/5xx, persist `*_UNKNOWN`, publish only an audit event, and reconcile by the original key.
- Capture only after inventory commit.
- Cancel uncaptured authorization or refund captured funds during compensation.

- If an inventory reservation expires during an unknown authorization outcome, the order enters `WAITING_PAYMENT_RESOLUTION_AFTER_EXPIRY`. A later decline closes the order; a later authorization is cancelled with the original deterministic compensation key before cancellation completes.
