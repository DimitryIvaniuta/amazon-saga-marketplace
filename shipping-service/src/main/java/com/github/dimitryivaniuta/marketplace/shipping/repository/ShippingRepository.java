package com.github.dimitryivaniuta.marketplace.shipping.repository;

import com.github.dimitryivaniuta.marketplace.common.event.WorkflowPayloads;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** Shipment persistence with order-level uniqueness. */
@Repository
@RequiredArgsConstructor
public class ShippingRepository {
    /** Reactive SQL client. */
    private final DatabaseClient databaseClient;

    /** @param command shipping command @param shipmentId id @param tracking tracking @return shipment */
    public Mono<ShipmentRow> create(
            WorkflowPayloads.ShippingCreate command, UUID shipmentId, String tracking) {
        return databaseClient.sql("""
                INSERT INTO shipment(id, order_id, user_id, status, tracking_number,
                                     recipient, address_line1, city, postal_code, country,
                                     created_at, updated_at)
                VALUES (:id, :orderId, :userId, 'CREATED', :tracking, :recipient,
                        :line1, :city, :postalCode, :country, now(), now())
                ON CONFLICT (order_id) DO NOTHING
                """).bind("id", shipmentId).bind("orderId", command.orderId())
                .bind("userId", command.userId()).bind("tracking", tracking)
                .bind("recipient", command.recipient()).bind("line1", command.addressLine1())
                .bind("city", command.city()).bind("postalCode", command.postalCode())
                .bind("country", command.country()).fetch().rowsUpdated()
                .then(byOrder(command.orderId()));
    }

    /** @param orderId order @return shipment */
    public Mono<ShipmentRow> byOrder(UUID orderId) {
        return databaseClient.sql("""
                SELECT id, order_id, user_id, status, tracking_number, recipient,
                       address_line1, city, postal_code, country, created_at, updated_at
                  FROM shipment WHERE order_id = :orderId
                """).bind("orderId", orderId).map((row, metadata) -> new ShipmentRow(
                        row.get("id", UUID.class), row.get("order_id", UUID.class),
                        row.get("user_id", UUID.class), row.get("status", String.class),
                        row.get("tracking_number", String.class), row.get("recipient", String.class),
                        row.get("address_line1", String.class), row.get("city", String.class),
                        row.get("postal_code", String.class), row.get("country", String.class),
                        row.get("created_at", OffsetDateTime.class).toInstant(),
                        row.get("updated_at", OffsetDateTime.class).toInstant())).one();
    }

    /** Immutable shipment projection. */
    public record ShipmentRow(
            UUID id, UUID orderId, UUID userId, String status, String trackingNumber,
            String recipient, String addressLine1, String city, String postalCode,
            String country, java.time.Instant createdAt, java.time.Instant updatedAt) { }
}
