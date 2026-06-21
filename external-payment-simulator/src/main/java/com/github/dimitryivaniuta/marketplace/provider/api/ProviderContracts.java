package com.github.dimitryivaniuta.marketplace.provider.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** External provider HTTP contracts. */
public final class ProviderContracts {
    private ProviderContracts() {
        throw new IllegalStateException("Utility class");
    }

    /** @param orderId merchant order @param amountMinor amount @param currency currency @param paymentToken test token */
    public record AuthorizeRequest(
            @NotNull UUID orderId, @Positive long amountMinor,
            @NotBlank String currency, @NotBlank String paymentToken) { }
    /** @param paymentId provider payment @param status provider state @param reference public reference */
    public record ProviderResponse(UUID paymentId, String status, String reference) { }
}
