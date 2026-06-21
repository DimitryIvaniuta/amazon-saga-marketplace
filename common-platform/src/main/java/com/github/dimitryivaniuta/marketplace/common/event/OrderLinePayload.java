package com.github.dimitryivaniuta.marketplace.common.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable item line transferred between checkout services.
 *
 * @param skuId item variant identifier
 * @param quantity requested quantity
 * @param unitPriceMinor immutable unit price in the currency's minor units
 * @param currency ISO-4217 currency code
 */
public record OrderLinePayload(UUID skuId, int quantity, long unitPriceMinor, String currency) {

    /** Validates line invariants. */
    public OrderLinePayload {
        Objects.requireNonNull(skuId, "skuId");
        Objects.requireNonNull(currency, "currency");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPriceMinor < 0) {
            throw new IllegalArgumentException("unitPriceMinor must not be negative");
        }
    }

    /**
     * Calculates the immutable line total.
     *
     * @return total in minor currency units
     */
    public long totalMinor() {
        return Math.multiplyExact(unitPriceMinor, quantity);
    }
}
