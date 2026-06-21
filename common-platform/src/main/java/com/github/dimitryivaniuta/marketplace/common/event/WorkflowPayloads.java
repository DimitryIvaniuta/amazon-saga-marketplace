package com.github.dimitryivaniuta.marketplace.common.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Version-one payload contracts shared by Saga participants. Contract changes
 * require a new event type/version rather than silently changing these records.
 */
public final class WorkflowPayloads {

    private WorkflowPayloads() {
        throw new IllegalStateException("Utility class");
    }

    /** @param orderId order id @param userId user id @param lines immutable lines @param expiresAt reservation expiry */
    public record InventoryReserve(UUID orderId, UUID userId, List<OrderLinePayload> lines, Instant expiresAt) { }
    /** @param orderId order id */
    public record OrderReference(UUID orderId) { }
    /** @param orderId order id @param code stable failure code @param message safe message */
    public record Failure(UUID orderId, String code, String message) { }
    /** @param orderId order id @param amountMinor amount @param currency currency @param paymentToken opaque provider token */
    public record PaymentAuthorize(UUID orderId, long amountMinor, String currency, String paymentToken) { }
    /** @param orderId order id @param externalReference provider reference */
    public record PaymentResult(UUID orderId, String externalReference) { }
    /**
     * Shipping request.
     * @param orderId order id
     * @param userId customer id
     * @param recipient recipient
     * @param addressLine1 first address line
     * @param city city
     * @param postalCode postal code
     * @param country ISO country
     * @param lines immutable order lines
     */
    public record ShippingCreate(
            UUID orderId, UUID userId, String recipient, String addressLine1,
            String city, String postalCode, String country, List<OrderLinePayload> lines) { }
    /** @param orderId order id @param shipmentId shipment id @param trackingNumber tracking number */
    public record ShippingResult(UUID orderId, UUID shipmentId, String trackingNumber) { }
    /**
     * Immutable audit payload.
     * @param source source service
     * @param action action name
     * @param aggregateType aggregate type
     * @param aggregateId aggregate id
     * @param outcome outcome
     * @param details bounded details
     * @param occurredAt occurrence time
     */
    public record Audit(
            String source, String action, String aggregateType, String aggregateId,
            String outcome, String details, Instant occurredAt) { }
}
