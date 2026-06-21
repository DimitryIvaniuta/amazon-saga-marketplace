# ADR 0001: Orchestrated checkout Saga

## Decision

Use the order service as an explicit Saga orchestrator. Participants expose asynchronous commands and outcome events through Kafka.

## Rationale

The workflow has ordered business invariants, visible compensation, and operator-facing state. An explicit transition table makes invalid and out-of-order events fail visibly. Choreography was rejected because the complete payment/stock/shipping policy would be scattered across services.

## Consequences

The orchestrator is a workflow dependency but not a data owner for inventory, payment, or shipping. Commands and events are versioned. All consumers must be idempotent.
