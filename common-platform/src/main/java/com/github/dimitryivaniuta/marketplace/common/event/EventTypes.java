package com.github.dimitryivaniuta.marketplace.common.event;

/**
 * Stable names of marketplace commands and events.
 */
public final class EventTypes {

    /** Inventory reservation command. */
    public static final String INVENTORY_RESERVE_REQUESTED = "inventory.reserve.requested.v1";
    /** Inventory reservation success event. */
    public static final String INVENTORY_RESERVED = "inventory.reserved.v1";
    /** Inventory reservation rejection event. */
    public static final String INVENTORY_REJECTED = "inventory.rejected.v1";
    /** Inventory commit command. */
    public static final String INVENTORY_COMMIT_REQUESTED = "inventory.commit.requested.v1";
    /** Inventory commit success event. */
    public static final String INVENTORY_COMMITTED = "inventory.committed.v1";
    /** Inventory release command. */
    public static final String INVENTORY_RELEASE_REQUESTED = "inventory.release.requested.v1";
    /** Inventory release success event. */
    public static final String INVENTORY_RELEASED = "inventory.released.v1";
    /** Inventory restock command after a committed sale is compensated. */
    public static final String INVENTORY_RESTOCK_REQUESTED = "inventory.restock.requested.v1";
    /** Inventory restock success event. */
    public static final String INVENTORY_RESTOCKED = "inventory.restocked.v1";
    /** Reservation expiry event. */
    public static final String INVENTORY_RESERVATION_EXPIRED = "inventory.reservation.expired.v1";

    /** Payment authorization command. */
    public static final String PAYMENT_AUTHORIZE_REQUESTED = "payment.authorize.requested.v1";
    /** Payment authorization success event. */
    public static final String PAYMENT_AUTHORIZED = "payment.authorized.v1";
    /** Payment authorization failure event. */
    public static final String PAYMENT_AUTHORIZATION_FAILED = "payment.authorization.failed.v1";
    /** Payment capture command. */
    public static final String PAYMENT_CAPTURE_REQUESTED = "payment.capture.requested.v1";
    /** Payment capture success event. */
    public static final String PAYMENT_CAPTURED = "payment.captured.v1";
    /** Payment capture failure event. */
    public static final String PAYMENT_CAPTURE_FAILED = "payment.capture.failed.v1";
    /** Payment authorization cancellation command. */
    public static final String PAYMENT_CANCEL_REQUESTED = "payment.cancel.requested.v1";
    /** Payment authorization cancellation event. */
    public static final String PAYMENT_CANCELLED = "payment.cancelled.v1";
    /** Payment refund command. */
    public static final String PAYMENT_REFUND_REQUESTED = "payment.refund.requested.v1";
    /** Payment refund success event. */
    public static final String PAYMENT_REFUNDED = "payment.refunded.v1";
    /** Generic payment compensation failure event. */
    public static final String PAYMENT_COMPENSATION_FAILED = "payment.compensation.failed.v1";

    /** Shipping creation command. */
    public static final String SHIPPING_CREATE_REQUESTED = "shipping.create.requested.v1";
    /** Shipping creation success event. */
    public static final String SHIPPING_CREATED = "shipping.created.v1";
    /** Shipping creation failure event. */
    public static final String SHIPPING_FAILED = "shipping.failed.v1";

    /** Immutable audit event. */
    public static final String AUDIT_RECORDED = "audit.recorded.v1";

    private EventTypes() {
        throw new IllegalStateException("Utility class");
    }
}
