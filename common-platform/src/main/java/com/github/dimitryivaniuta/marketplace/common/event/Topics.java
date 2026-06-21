package com.github.dimitryivaniuta.marketplace.common.event;

/**
 * Kafka topic names used by the marketplace.
 */
public final class Topics {

    /** Inventory commands topic. */
    public static final String INVENTORY_COMMANDS = "marketplace.inventory.commands.v1";
    /** Inventory events topic. */
    public static final String INVENTORY_EVENTS = "marketplace.inventory.events.v1";
    /** Payment commands topic. */
    public static final String PAYMENT_COMMANDS = "marketplace.payment.commands.v1";
    /** Payment events topic. */
    public static final String PAYMENT_EVENTS = "marketplace.payment.events.v1";
    /** Shipping commands topic. */
    public static final String SHIPPING_COMMANDS = "marketplace.shipping.commands.v1";
    /** Shipping events topic. */
    public static final String SHIPPING_EVENTS = "marketplace.shipping.events.v1";
    /** Audit events topic. */
    public static final String AUDIT_EVENTS = "marketplace.audit.events.v1";

    private Topics() {
        throw new IllegalStateException("Utility class");
    }
}
