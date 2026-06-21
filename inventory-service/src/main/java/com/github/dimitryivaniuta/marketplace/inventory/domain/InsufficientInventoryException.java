package com.github.dimitryivaniuta.marketplace.inventory.domain;

import java.util.UUID;

/** Indicates an atomic reservation could not reserve the requested quantity. */
public class InsufficientInventoryException extends RuntimeException {
    /** Unavailable SKU. */
    private final UUID skuId;
    /** @param skuId unavailable SKU */
    public InsufficientInventoryException(UUID skuId) {
        super("Insufficient inventory for SKU " + skuId);
        this.skuId = skuId;
    }
    /** @return unavailable SKU */
    public UUID getSkuId() { return skuId; }
}
