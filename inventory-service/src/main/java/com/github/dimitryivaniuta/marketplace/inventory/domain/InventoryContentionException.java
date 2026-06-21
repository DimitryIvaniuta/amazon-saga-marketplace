package com.github.dimitryivaniuta.marketplace.inventory.domain;

import com.github.dimitryivaniuta.marketplace.common.messaging.TransientContentionException;
import java.util.UUID;
import lombok.Getter;

/**
 * Transient failure indicating that stock exists but all useful hot-SKU buckets
 * were concurrently locked. Kafka retries this condition instead of converting
 * temporary contention into a false out-of-stock response.
 */
@Getter
public class InventoryContentionException extends TransientContentionException {

    /** Contended SKU. */
    private final UUID skuId;

    /** @param skuId contended SKU */
    public InventoryContentionException(UUID skuId) {
        super("Inventory for SKU " + skuId + " is temporarily contended");
        this.skuId = skuId;
    }
}
