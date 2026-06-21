package com.github.dimitryivaniuta.marketplace.cart.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Cart API contracts. */
public final class CartContracts {
    private CartContracts() { throw new IllegalStateException("Utility class"); }

    /** @param skuId SKU id @param quantity quantity */
    public record ChangeItem(@NotNull UUID skuId, @Min(1) @Max(99) int quantity) { }
    /** @param skuId SKU id @param quantity quantity @param addedAt creation time */
    public record Item(UUID skuId, int quantity, Instant addedAt) { }
    /** @param cartId cart id @param userId owner id @param items cart items @param updatedAt update time */
    public record Cart(UUID cartId, UUID userId, List<Item> items, Instant updatedAt) { }
}
