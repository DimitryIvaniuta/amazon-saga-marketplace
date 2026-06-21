package com.github.dimitryivaniuta.marketplace.inventory.repository;

import com.github.dimitryivaniuta.marketplace.inventory.domain.InventoryLine;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** PostgreSQL inventory and reservation persistence gateway. */
@Repository
@RequiredArgsConstructor
public class InventoryRepository {

    /** Default stripe count used for new SKUs. */
    public static final int DEFAULT_BUCKET_COUNT = 16;

    /** Reactive SQL client. */
    private final DatabaseClient databaseClient;

    /** @param orderId order @param userId user @param expiresAt expiry @return completion */
    public Mono<Void> createReservation(UUID orderId, UUID userId, Instant expiresAt) {
        return databaseClient.sql("""
                INSERT INTO inventory_reservation(id, order_id, user_id, status, expires_at, created_at, updated_at)
                VALUES (:id, :orderId, :userId, 'RESERVED', :expiresAt, now(), now())
                """)
                .bind("id", UUID.randomUUID()).bind("orderId", orderId).bind("userId", userId)
                .bind("expiresAt", OffsetDateTime.ofInstant(expiresAt, java.time.ZoneOffset.UTC))
                .fetch().rowsUpdated().then();
    }

    /**
     * Reserves stock across striped rows without waiting behind a locked hot-SKU row.
     *
     * <p>The statement locks only currently available buckets with
     * {@code FOR UPDATE SKIP LOCKED}, rotates the bucket order using the order id,
     * calculates an exact allocation, updates all selected buckets, and persists
     * the allocation in one atomic SQL statement. A zero result can mean genuine
     * insufficient stock or temporary contention; callers distinguish those by
     * reading the aggregate available quantity.</p>
     *
     * @param orderId order id
     * @param skuId SKU id
     * @param quantity quantity
     * @return {@code 1} when fully reserved, otherwise {@code 0}
     */
    public Mono<Long> reserveLine(UUID orderId, UUID skuId, int quantity) {
        int bucketSeed = (orderId.hashCode() ^ skuId.hashCode()) & Integer.MAX_VALUE;
        return databaseClient.sql("""
                WITH reservation AS MATERIALIZED (
                    SELECT id
                      FROM inventory_reservation
                     WHERE order_id = :orderId
                ),
                locked AS MATERIALIZED (
                    SELECT b.bucket_index,
                           b.available_quantity,
                           mod(
                               b.bucket_index - mod(:bucketSeed, i.bucket_count) + i.bucket_count,
                               i.bucket_count
                           ) AS priority
                      FROM inventory_bucket b
                      JOIN inventory i ON i.sku_id = b.sku_id
                     CROSS JOIN reservation r
                     WHERE b.sku_id = :skuId
                       AND b.available_quantity > 0
                     ORDER BY priority
                     LIMIT :quantity
                     FOR UPDATE OF b SKIP LOCKED
                ),
                ranked AS (
                    SELECT bucket_index,
                           available_quantity,
                           priority,
                           COALESCE(SUM(available_quantity) OVER (
                               ORDER BY priority
                               ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0) AS allocated_before
                      FROM locked
                ),
                allocation AS (
                    SELECT bucket_index,
                           LEAST(
                               available_quantity,
                               GREATEST(CAST(:quantity AS bigint) - allocated_before, 0)
                           )::integer AS allocated
                      FROM ranked
                ),
                capacity AS (
                    SELECT COALESCE(SUM(allocated), 0)::integer AS allocated_total
                      FROM allocation
                ),
                updated AS (
                    UPDATE inventory_bucket b
                       SET available_quantity = b.available_quantity - a.allocated,
                           reserved_quantity = b.reserved_quantity + a.allocated,
                           version = b.version + 1,
                           updated_at = now()
                      FROM allocation a, capacity c
                     WHERE b.sku_id = :skuId
                       AND b.bucket_index = a.bucket_index
                       AND a.allocated > 0
                       AND c.allocated_total = :quantity
                    RETURNING b.bucket_index
                ),
                inserted AS (
                    INSERT INTO inventory_reservation_line(
                        reservation_id, sku_id, bucket_index, quantity)
                    SELECT r.id, :skuId, a.bucket_index, a.allocated
                      FROM reservation r
                      JOIN allocation a ON a.allocated > 0
                      JOIN updated u ON u.bucket_index = a.bucket_index
                    RETURNING quantity
                )
                SELECT (CASE WHEN COALESCE(SUM(quantity), 0) = :quantity THEN 1 ELSE 0 END)::bigint AS reserved
                  FROM inserted
                """)
                .bind("orderId", orderId)
                .bind("skuId", skuId)
                .bind("quantity", quantity)
                .bind("bucketSeed", bucketSeed)
                .map((row, metadata) -> row.get("reserved", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    /** @param skuId SKU @return aggregate currently available quantity */
    public Mono<Long> availableQuantity(UUID skuId) {
        return databaseClient.sql("""
                SELECT COALESCE(SUM(available_quantity), 0)::bigint AS available
                  FROM inventory_bucket
                 WHERE sku_id = :skuId
                """)
                .bind("skuId", skuId)
                .map((row, metadata) -> row.get("available", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    /** @param orderId order @return current reservation status */
    public Mono<String> reservationStatus(UUID orderId) {
        return databaseClient.sql("SELECT status FROM inventory_reservation WHERE order_id = :orderId")
                .bind("orderId", orderId)
                .map((row, metadata) -> row.get("status", String.class)).one();
    }

    /** @param orderId order @param expected expected status @param next next status @return changed rows */
    public Mono<Long> transition(UUID orderId, String expected, String next) {
        return databaseClient.sql("""
                UPDATE inventory_reservation
                   SET status = :next, updated_at = now()
                 WHERE order_id = :orderId AND status = :expected
                """)
                .bind("next", next).bind("orderId", orderId).bind("expected", expected)
                .fetch().rowsUpdated();
    }

    /** @param orderId order @return exact reservation bucket allocations */
    public Flux<InventoryLine> lines(UUID orderId) {
        return databaseClient.sql("""
                SELECT l.sku_id, l.bucket_index, l.quantity
                  FROM inventory_reservation_line l
                  JOIN inventory_reservation r ON r.id = l.reservation_id
                 WHERE r.order_id = :orderId
                 ORDER BY l.sku_id, l.bucket_index
                """)
                .bind("orderId", orderId)
                .map((row, metadata) -> new InventoryLine(
                        row.get("sku_id", UUID.class),
                        row.get("bucket_index", Short.class),
                        row.get("quantity", Integer.class)))
                .all();
    }

    /** @param line line @return completion */
    public Mono<Void> commitLine(InventoryLine line) {
        return updateAllocatedBucket(line, """
                reserved_quantity = reserved_quantity - :quantity,
                sold_quantity = sold_quantity + :quantity
                """, "reserved_quantity >= :quantity", "Reserved stock invariant violated");
    }

    /** @param line line @return completion */
    public Mono<Void> releaseLine(InventoryLine line) {
        return updateAllocatedBucket(line, """
                available_quantity = available_quantity + :quantity,
                reserved_quantity = reserved_quantity - :quantity
                """, "reserved_quantity >= :quantity", "Reserved stock invariant violated");
    }

    /** @param line line @return completion */
    public Mono<Void> restockLine(InventoryLine line) {
        return updateAllocatedBucket(line, """
                available_quantity = available_quantity + :quantity,
                sold_quantity = sold_quantity - :quantity
                """, "sold_quantity >= :quantity", "Sold stock invariant violated");
    }

    /** @return expired reserved order ids */
    public Flux<UUID> expiredReservations() {
        return databaseClient.sql("""
                SELECT order_id FROM inventory_reservation
                 WHERE status = 'RESERVED' AND expires_at < now()
                 ORDER BY expires_at
                 LIMIT 100
                """)
                .map((row, metadata) -> row.get("order_id", UUID.class)).all();
    }

    /** @return all inventory rows aggregated across hot-SKU buckets */
    public Flux<StockView> stock() {
        return databaseClient.sql("""
                SELECT sku_id,
                       SUM(available_quantity)::integer AS available_quantity,
                       SUM(reserved_quantity)::integer AS reserved_quantity,
                       SUM(sold_quantity)::integer AS sold_quantity,
                       SUM(version)::bigint AS version,
                       MAX(updated_at) AS updated_at,
                       COUNT(*)::integer AS bucket_count
                  FROM inventory_bucket
                 GROUP BY sku_id
                 ORDER BY sku_id
                """)
                .map((row, metadata) -> new StockView(
                        row.get("sku_id", UUID.class),
                        row.get("available_quantity", Integer.class),
                        row.get("reserved_quantity", Integer.class),
                        row.get("sold_quantity", Integer.class),
                        row.get("version", Long.class),
                        row.get("bucket_count", Integer.class),
                        row.get("updated_at", OffsetDateTime.class).toInstant()))
                .all();
    }

    /**
     * Creates a SKU if needed and evenly distributes administrative availability
     * across all stripes without changing reserved or sold counters.
     *
     * @param skuId SKU
     * @param available available quantity
     * @return completion
     */
    public Mono<Void> setAvailable(UUID skuId, int available) {
        return databaseClient.sql("""
                WITH upsert_inventory AS (
                    INSERT INTO inventory(
                        sku_id, available_quantity, reserved_quantity, sold_quantity,
                        version, updated_at, bucket_count)
                    VALUES (:skuId, :available, 0, 0, 0, now(), :bucketCount)
                    ON CONFLICT (sku_id) DO UPDATE
                    SET available_quantity = EXCLUDED.available_quantity,
                        version = inventory.version + 1,
                        updated_at = now()
                    RETURNING sku_id, bucket_count
                )
                INSERT INTO inventory_bucket(
                    sku_id, bucket_index, available_quantity, reserved_quantity,
                    sold_quantity, version, updated_at)
                SELECT i.sku_id,
                       bucket.bucket_index,
                       (:available / i.bucket_count)
                           + CASE WHEN bucket.bucket_index < (:available % i.bucket_count) THEN 1 ELSE 0 END,
                       0,
                       0,
                       0,
                       now()
                  FROM upsert_inventory i
                 CROSS JOIN LATERAL generate_series(0, i.bucket_count - 1) AS bucket(bucket_index)
                ON CONFLICT (sku_id, bucket_index) DO UPDATE
                SET available_quantity = EXCLUDED.available_quantity,
                    version = inventory_bucket.version + 1,
                    updated_at = now()
                """)
                .bind("skuId", skuId)
                .bind("available", available)
                .bind("bucketCount", DEFAULT_BUCKET_COUNT)
                .fetch().rowsUpdated().then();
    }

    private Mono<Void> updateAllocatedBucket(
            InventoryLine line, String assignments, String invariant, String errorMessage) {
        String sql = """
                UPDATE inventory_bucket
                   SET %s,
                       version = version + 1,
                       updated_at = now()
                 WHERE sku_id = :skuId
                   AND bucket_index = :bucketIndex
                   AND %s
                """.formatted(assignments, invariant);
        return databaseClient.sql(sql)
                .bind("quantity", line.quantity())
                .bind("skuId", line.skuId())
                .bind("bucketIndex", line.bucketIndex())
                .fetch().rowsUpdated()
                .flatMap(count -> count == 1L
                        ? Mono.empty()
                        : Mono.error(new IllegalStateException(errorMessage)));
    }

    /**
     * @param skuId SKU
     * @param availableQuantity available
     * @param reservedQuantity reserved
     * @param soldQuantity sold
     * @param version aggregate bucket version
     * @param bucketCount stripe count
     * @param updatedAt updated
     */
    public record StockView(
            UUID skuId,
            int availableQuantity,
            int reservedQuantity,
            int soldQuantity,
            long version,
            int bucketCount,
            Instant updatedAt) { }
}
