package com.github.dimitryivaniuta.marketplace.common.messaging;

/**
 * Marker exception for very short-lived resource contention that should be
 * retried faster than ordinary infrastructure or provider failures.
 */
public class TransientContentionException extends RuntimeException {

    /** @param message diagnostic message */
    public TransientContentionException(String message) {
        super(message);
    }
}
