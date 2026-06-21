package com.github.dimitryivaniuta.marketplace.inventory.domain;

import java.util.UUID;

/**
 * Exact stock-bucket allocation belonging to an order reservation.
 *
 * @param skuId SKU identifier
 * @param bucketIndex striped inventory bucket
 * @param quantity reserved quantity
 */
public record InventoryLine(UUID skuId, short bucketIndex, int quantity) { }
