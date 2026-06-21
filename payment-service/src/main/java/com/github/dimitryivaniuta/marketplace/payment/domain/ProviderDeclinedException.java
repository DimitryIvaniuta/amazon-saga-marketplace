package com.github.dimitryivaniuta.marketplace.payment.domain;

/** Signals a definitive provider rejection that is safe to expose to the Saga. */
public class ProviderDeclinedException extends RuntimeException {
    /** @param message safe provider rejection */
    public ProviderDeclinedException(String message) {
        super(message);
    }
}
