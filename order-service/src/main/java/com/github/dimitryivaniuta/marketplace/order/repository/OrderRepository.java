package com.github.dimitryivaniuta.marketplace.order.repository;

import com.github.dimitryivaniuta.marketplace.common.event.OrderLinePayload;
import com.github.dimitryivaniuta.marketplace.order.api.OrderContracts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Order, Saga, and checkout-idempotency persistence gateway. */
@Repository
@RequiredArgsConstructor
public class OrderRepository {

    /** Reactive SQL client. */
    private final DatabaseClient databaseClient;

    /** @param userId user id @param key idempotency key @return existing claim */
    public Mono<IdempotencyClaim> findClaim(UUID userId, String key) {
        return databaseClient.sql("""
                SELECT order_id, request_hash FROM checkout_idempotency
                 WHERE user_id = :userId AND idempotency_key = :key
                """)
                .bind("userId", userId).bind("key", key)
                .map((row, metadata) -> new IdempotencyClaim(
                        row.get("order_id", UUID.class), row.get("request_hash", String.class), false))
                .one();
    }

    /**
     * Claims an idempotency key or returns the existing order mapping.
     * @param userId user id @param key client key @param requestHash canonical hash @param orderId proposed order
     * @return claim result
     */
    public Mono<IdempotencyClaim> claim(UUID userId, String key, String requestHash, UUID orderId) {
        return databaseClient.sql("""
                INSERT INTO checkout_idempotency(user_id, idempotency_key, request_hash, order_id, created_at)
                VALUES (:userId, :key, :requestHash, :orderId, now())
                ON CONFLICT (user_id, idempotency_key) DO NOTHING
                RETURNING order_id, request_hash
                """)
                .bind("userId", userId).bind("key", key).bind("requestHash", requestHash).bind("orderId", orderId)
                .map((row, metadata) -> new IdempotencyClaim(
                        row.get("order_id", UUID.class), row.get("request_hash", String.class), true))
                .one()
                .switchIfEmpty(databaseClient.sql("""
                        SELECT order_id, request_hash FROM checkout_idempotency
                         WHERE user_id = :userId AND idempotency_key = :key
                        """)
                        .bind("userId", userId).bind("key", key)
                        .map((row, metadata) -> new IdempotencyClaim(
                                row.get("order_id", UUID.class), row.get("request_hash", String.class), false))
                        .one());
    }

    /** @param orderId order @param userId user @param total total @param currency currency @param paymentToken token @param address address JSON @return completion */
    public Mono<Void> insertOrder(
            UUID orderId, UUID userId, long total, String currency, String paymentToken, String address) {
        return databaseClient.sql("""
                INSERT INTO customer_order(
                    id, user_id, status, total_minor, currency, payment_token, shipping_address,
                    created_at, updated_at)
                VALUES (:id, :userId, 'PENDING', :total, :currency, :paymentToken,
                        CAST(:address AS jsonb), now(), now())
                """)
                .bind("id", orderId).bind("userId", userId).bind("total", total)
                .bind("currency", currency).bind("paymentToken", paymentToken).bind("address", address)
                .fetch().rowsUpdated().then();
    }

    /** @param orderId order @param lines order lines @return completion */
    public Mono<Void> insertLines(UUID orderId, List<OrderLinePayload> lines) {
        return Flux.fromIterable(lines).concatMap(line -> databaseClient.sql("""
                INSERT INTO order_line(order_id, sku_id, quantity, unit_price_minor, currency)
                VALUES (:orderId, :skuId, :quantity, :price, :currency)
                """)
                .bind("orderId", orderId).bind("skuId", line.skuId()).bind("quantity", line.quantity())
                .bind("price", line.unitPriceMinor()).bind("currency", line.currency())
                .fetch().rowsUpdated()).then();
    }

    /** @param orderId order @return completion */
    public Mono<Void> insertSaga(UUID orderId) {
        return databaseClient.sql("""
                INSERT INTO order_saga(order_id, state, inventory_compensated, payment_compensated, version, updated_at)
                VALUES (:orderId, 'WAITING_INVENTORY', false, false, 0, now())
                """)
                .bind("orderId", orderId).fetch().rowsUpdated().then();
    }

    /** @param orderId order @return saga */
    public Mono<SagaRow> saga(UUID orderId) {
        return databaseClient.sql("""
                SELECT state, inventory_compensated, payment_compensated, version
                  FROM order_saga WHERE order_id = :orderId
                """)
                .bind("orderId", orderId)
                .map((row, metadata) -> new SagaRow(
                        row.get("state", String.class), Boolean.TRUE.equals(row.get("inventory_compensated", Boolean.class)),
                        Boolean.TRUE.equals(row.get("payment_compensated", Boolean.class)), row.get("version", Long.class)))
                .one();
    }

    /** @param orderId order @param expected current @param next next @return updated rows */
    public Mono<Long> transitionSaga(UUID orderId, String expected, String next) {
        return databaseClient.sql("""
                UPDATE order_saga SET state = :next, version = version + 1, updated_at = now()
                 WHERE order_id = :orderId AND state = :expected
                """)
                .bind("next", next).bind("orderId", orderId).bind("expected", expected)
                .fetch().rowsUpdated();
    }

    /** Removes the opaque checkout token after authorization reaches a known outcome. */
    public Mono<Void> clearPaymentToken(UUID orderId) {
        return databaseClient.sql("UPDATE customer_order SET payment_token = NULL, updated_at = now() WHERE id = :id")
                .bind("id", orderId).fetch().rowsUpdated().then();
    }

    /** @param orderId order @param status status @param failureCode nullable failure @return completion */
    public Mono<Void> updateOrder(UUID orderId, String status, String failureCode) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                UPDATE customer_order SET status = :status, failure_code = :failureCode, updated_at = now()
                 WHERE id = :orderId
                """).bind("status", status).bind("orderId", orderId);
        spec = failureCode == null ? spec.bindNull("failureCode", String.class) : spec.bind("failureCode", failureCode);
        return spec.fetch().rowsUpdated().then();
    }

    /** @param orderId order @param inventory inventory done @param payment payment done @return completion */
    public Mono<Void> markCompensation(UUID orderId, Boolean inventory, Boolean payment) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                UPDATE order_saga
                   SET inventory_compensated = COALESCE(:inventory, inventory_compensated),
                       payment_compensated = COALESCE(:payment, payment_compensated),
                       version = version + 1, updated_at = now()
                 WHERE order_id = :orderId
                """).bind("orderId", orderId);
        spec = inventory == null ? spec.bindNull("inventory", Boolean.class) : spec.bind("inventory", inventory);
        spec = payment == null ? spec.bindNull("payment", Boolean.class) : spec.bind("payment", payment);
        return spec.fetch().rowsUpdated().then();
    }

    /** @param orderId order @return immutable lines */
    public Flux<OrderLinePayload> lines(UUID orderId) {
        return databaseClient.sql("""
                SELECT sku_id, quantity, unit_price_minor, currency FROM order_line
                 WHERE order_id = :orderId ORDER BY sku_id
                """)
                .bind("orderId", orderId)
                .map((row, metadata) -> new OrderLinePayload(
                        row.get("sku_id", UUID.class), row.get("quantity", Integer.class),
                        row.get("unit_price_minor", Long.class), row.get("currency", String.class)))
                .all();
    }

    /** @param orderId order @return durable order creation time */
    public Mono<Instant> createdAt(UUID orderId) {
        return databaseClient.sql("SELECT created_at FROM customer_order WHERE id = :id")
                .bind("id", orderId)
                .map((row, metadata) -> row.get("created_at", OffsetDateTime.class).toInstant())
                .one();
    }

    /** @param orderId order @return payment data */
    public Mono<PaymentData> paymentData(UUID orderId) {
        return databaseClient.sql("SELECT total_minor, currency, payment_token FROM customer_order WHERE id = :id")
                .bind("id", orderId)
                .map((row, metadata) -> new PaymentData(
                        row.get("total_minor", Long.class), row.get("currency", String.class),
                        row.get("payment_token", String.class))).one();
    }

    /** @param orderId order @return shipping data */
    public Mono<ShippingData> shippingData(UUID orderId) {
        return databaseClient.sql("""
                SELECT user_id, shipping_address::text AS address
                  FROM customer_order WHERE id = :id
                """)
                .bind("id", orderId)
                .map((row, metadata) -> new ShippingData(
                        row.get("user_id", UUID.class), row.get("address", String.class)))
                .one();
    }

    /** @param orderId order @param userId owner @return order view */
    public Mono<OrderContracts.OrderView> view(UUID orderId, UUID userId) {
        return databaseClient.sql("""
                SELECT o.id, o.status, o.total_minor, o.currency, o.failure_code,
                       o.created_at, o.updated_at, s.state
                  FROM customer_order o JOIN order_saga s ON s.order_id = o.id
                 WHERE o.id = :orderId AND o.user_id = :userId
                """)
                .bind("orderId", orderId).bind("userId", userId)
                .map((row, metadata) -> new ViewHeader(
                        row.get("id", UUID.class), row.get("status", String.class), row.get("state", String.class),
                        row.get("total_minor", Long.class), row.get("currency", String.class),
                        row.get("failure_code", String.class), row.get("created_at", OffsetDateTime.class).toInstant(),
                        row.get("updated_at", OffsetDateTime.class).toInstant()))
                .one().flatMap(header -> lines(orderId).map(line -> new OrderContracts.Line(
                                line.skuId(), line.quantity(), line.unitPriceMinor(), line.currency()))
                        .collectList().map(lines -> new OrderContracts.OrderView(
                                header.id(), header.status(), header.state(), header.total(), header.currency(),
                                lines, header.failureCode(), header.createdAt(), header.updatedAt())));
    }

    /** @param value canonical value @return SHA-256 hash */
    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** @param orderId mapped order @param requestHash hash @param owner true when newly claimed */
    public record IdempotencyClaim(UUID orderId, String requestHash, boolean owner) { }
    /** @param state state @param inventoryCompensated flag @param paymentCompensated flag @param version version */
    public record SagaRow(String state, boolean inventoryCompensated, boolean paymentCompensated, long version) { }
    /** @param totalMinor total @param currency currency @param paymentToken token */
    public record PaymentData(long totalMinor, String currency, String paymentToken) { }
    /** @param userId order owner @param address shipping address JSON */
    public record ShippingData(UUID userId, String address) { }
    private record ViewHeader(UUID id, String status, String state, long total, String currency, String failureCode, Instant createdAt, Instant updatedAt) { }
}
