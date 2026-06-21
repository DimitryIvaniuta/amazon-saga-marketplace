package com.github.dimitryivaniuta.marketplace.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Catalog HTTP contracts. */
public final class CatalogContracts {

    private CatalogContracts() {
        throw new IllegalStateException("Utility class");
    }

    /** @param id SKU id @param skuCode merchant code @param attributes variant attributes @param priceMinor price @param currency currency @param active active flag */
    public record Variant(UUID id, String skuCode, Map<String, String> attributes, long priceMinor, String currency, boolean active) {
    }

    /** @param id product id @param name name @param description description @param category category @param active active flag @param variants variants @param createdAt creation time */
    public record Product(UUID id, String name, String description, String category, boolean active, List<Variant> variants, Instant createdAt) {
    }

    /** @param name name @param description description @param category category @param variants variants */
    public record CreateProduct(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 4000) String description,
            @NotBlank @Size(max = 100) String category,
            @NotEmpty List<@Valid CreateVariant> variants) {
    }

    /** @param skuCode merchant SKU @param attributes variant attributes @param priceMinor price @param currency ISO currency */
    public record CreateVariant(
            @NotBlank @Size(max = 100) String skuCode,
            @NotNull Map<@NotBlank String, @NotBlank String> attributes,
            @Min(0) long priceMinor,
            @NotBlank @Size(min = 3, max = 3) String currency) {
    }
}
