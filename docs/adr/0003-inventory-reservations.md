# ADR 0003: Checkout-time stock reservations

## Decision

Cart operations do not reserve stock. Checkout creates a short-lived inventory reservation with an atomic conditional update.

## Rationale

Holding inventory for every abandoned cart enables denial of inventory and creates poor availability. A SQL condition preventing `available_quantity` from going below zero provides the serialization point required to prevent overselling.

Expired reservations are released by an idempotent scheduled job. A committed reservation can be restocked only through a Saga compensation command.
