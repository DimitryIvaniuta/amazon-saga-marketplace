package com.github.dimitryivaniuta.marketplace.payment.domain;

/** Signals an unknown provider outcome that must be reconciled before any retry decision. */
public class ProviderAmbiguousException extends RuntimeException {
    /** @param message diagnostic message @param cause network cause */
    public ProviderAmbiguousException(String message, Throwable cause) {
        super(message, cause);
    }
}
