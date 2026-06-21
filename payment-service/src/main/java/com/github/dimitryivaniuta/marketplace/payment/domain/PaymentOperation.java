package com.github.dimitryivaniuta.marketplace.payment.domain;

/** Supported provider operations. */
public enum PaymentOperation {
    /** Reserve funds without moving them. */
    AUTHORIZE,
    /** Move previously authorized funds. */
    CAPTURE,
    /** Void an uncaptured authorization. */
    CANCEL,
    /** Return captured funds. */
    REFUND
}
