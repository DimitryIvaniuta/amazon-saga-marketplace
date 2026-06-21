package com.github.dimitryivaniuta.marketplace.order.domain;

import java.util.Map;
import java.util.Set;

/**
 * Explicit checkout Saga transition policy. Keeping transitions pure makes
 * illegal or out-of-order events visible rather than silently corrupting state.
 */
public final class SagaStateMachine {

    /** Allowed next states by current state. */
    private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("WAITING_INVENTORY", Set.of("WAITING_PAYMENT_AUTHORIZATION", "CANCELLED")),
            Map.entry("WAITING_PAYMENT_AUTHORIZATION", Set.of(
                    "WAITING_INVENTORY_COMMIT", "COMPENSATING_INVENTORY",
                    "WAITING_PAYMENT_RESOLUTION_AFTER_EXPIRY")),
            Map.entry("WAITING_PAYMENT_RESOLUTION_AFTER_EXPIRY", Set.of(
                    "COMPENSATING_PAYMENT", "CANCELLED", "MANUAL_INTERVENTION")),
            Map.entry("WAITING_INVENTORY_COMMIT", Set.of("WAITING_PAYMENT_CAPTURE", "COMPENSATING_PAYMENT")),
            Map.entry("WAITING_PAYMENT_CAPTURE", Set.of("WAITING_SHIPPING", "COMPENSATING_CAPTURE")),
            Map.entry("WAITING_SHIPPING", Set.of("COMPLETED", "COMPENSATING_FULFILLMENT")),
            Map.entry("COMPENSATING_INVENTORY", Set.of("CANCELLED", "MANUAL_INTERVENTION")),
            Map.entry("COMPENSATING_PAYMENT", Set.of("CANCELLED", "MANUAL_INTERVENTION")),
            Map.entry("COMPENSATING_CAPTURE", Set.of("CANCELLED", "MANUAL_INTERVENTION")),
            Map.entry("COMPENSATING_FULFILLMENT", Set.of("CANCELLED", "MANUAL_INTERVENTION")),
            Map.entry("MANUAL_INTERVENTION", Set.of()),
            Map.entry("COMPLETED", Set.of()),
            Map.entry("CANCELLED", Set.of()));

    private SagaStateMachine() { throw new IllegalStateException("Utility class"); }

    /**
     * Validates a transition.
     * @param current current state
     * @param next next state
     */
    public static void requireAllowed(String current, String next) {
        if (!ALLOWED.getOrDefault(current, Set.of()).contains(next)) {
            throw new IllegalStateException("Illegal Saga transition " + current + " -> " + next);
        }
    }
}
