package com.github.dimitryivaniuta.marketplace.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Order checkout and query contracts. */
public final class OrderContracts {
    private OrderContracts() { throw new IllegalStateException("Utility class"); }

    /** @param paymentToken opaque provider token @param shippingAddress delivery address */
    public record Checkout(@NotBlank @Size(max = 256) String paymentToken, @Valid ShippingAddress shippingAddress) { }
    /** @param recipient recipient @param addressLine1 address @param city city @param postalCode postal code @param country country */
    public record ShippingAddress(
            @NotBlank @Size(max = 200) String recipient,
            @NotBlank @Size(max = 300) String addressLine1,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 32) String postalCode,
            @NotBlank @Size(min = 2, max = 2) String country) { }
    /** @param skuId SKU @param quantity quantity @param unitPriceMinor unit price @param currency currency */
    public record Line(UUID skuId, int quantity, long unitPriceMinor, String currency) { }
    /** @param orderId order id @param status order status @param sagaState workflow state @param totalMinor total @param currency currency @param lines lines @param failureCode failure @param createdAt creation @param updatedAt update */
    public record OrderView(
            UUID orderId, String status, String sagaState, long totalMinor, String currency,
            List<Line> lines, String failureCode, Instant createdAt, Instant updatedAt) { }
    /** @param orderId order id @param status initial status */
    public record CheckoutAccepted(UUID orderId, String status) { }
}
