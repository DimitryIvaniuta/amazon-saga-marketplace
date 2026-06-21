package com.github.dimitryivaniuta.marketplace.cart.repository;

import com.github.dimitryivaniuta.marketplace.cart.api.CartContracts;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Reactive cart persistence gateway. */
@Repository
@RequiredArgsConstructor
public class CartRepository {

    /** Reactive SQL client. */
    private final DatabaseClient databaseClient;

    /** @param userId owner @return cart id */
    public Mono<UUID> ensureCart(UUID userId) {
        UUID generated = UUID.randomUUID();
        return databaseClient.sql("""
                INSERT INTO cart(id, user_id, created_at, updated_at)
                VALUES (:id, :userId, now(), now())
                ON CONFLICT (user_id) DO UPDATE SET updated_at = cart.updated_at
                RETURNING id
                """)
                .bind("id", generated).bind("userId", userId)
                .map((row, metadata) -> row.get("id", UUID.class)).one();
    }

    /** @param cartId cart id @param skuId SKU @param quantity quantity @return completion */
    public Mono<Void> upsertItem(UUID cartId, UUID skuId, int quantity) {
        return databaseClient.sql("""
                INSERT INTO cart_item(cart_id, sku_id, quantity, added_at, updated_at)
                VALUES (:cartId, :skuId, :quantity, now(), now())
                ON CONFLICT (cart_id, sku_id)
                DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = now()
                """)
                .bind("cartId", cartId).bind("skuId", skuId).bind("quantity", quantity)
                .fetch().rowsUpdated()
                .then(touch(cartId));
    }

    /** @param cartId cart id @param skuId SKU @return completion */
    public Mono<Void> deleteItem(UUID cartId, UUID skuId) {
        return databaseClient.sql("DELETE FROM cart_item WHERE cart_id = :cartId AND sku_id = :skuId")
                .bind("cartId", cartId).bind("skuId", skuId).fetch().rowsUpdated().then(touch(cartId));
    }

    /** @param cartId cart id @return completion */
    public Mono<Void> clear(UUID cartId) {
        return databaseClient.sql("DELETE FROM cart_item WHERE cart_id = :cartId")
                .bind("cartId", cartId).fetch().rowsUpdated().then(touch(cartId));
    }

    /** @param userId owner @return full cart */
    public Mono<CartContracts.Cart> find(UUID userId) {
        return databaseClient.sql("SELECT id, updated_at FROM cart WHERE user_id = :userId")
                .bind("userId", userId)
                .map((row, metadata) -> new CartHeader(
                        row.get("id", UUID.class), row.get("updated_at", OffsetDateTime.class).toInstant()))
                .one()
                .flatMap(header -> items(header.id()).collectList()
                        .map(items -> new CartContracts.Cart(header.id(), userId, items, header.updatedAt())));
    }

    private Flux<CartContracts.Item> items(UUID cartId) {
        return databaseClient.sql("""
                SELECT sku_id, quantity, added_at
                  FROM cart_item
                 WHERE cart_id = :cartId
                 ORDER BY added_at
                """)
                .bind("cartId", cartId)
                .map((row, metadata) -> new CartContracts.Item(
                        row.get("sku_id", UUID.class), row.get("quantity", Integer.class),
                        row.get("added_at", OffsetDateTime.class).toInstant()))
                .all();
    }

    private Mono<Void> touch(UUID cartId) {
        return databaseClient.sql("UPDATE cart SET updated_at = now() WHERE id = :id")
                .bind("id", cartId).fetch().rowsUpdated().then();
    }

    private record CartHeader(UUID id, java.time.Instant updatedAt) { }
}
