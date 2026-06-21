package com.github.dimitryivaniuta.marketplace.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests catalog value contracts. */
class CatalogContractsTest {
    /** Verifies a variant preserves arbitrary item attributes. */
    @Test
    void shouldPreserveVariantAttributes() {
        var variant = new CatalogContracts.Variant(
                UUID.randomUUID(), "SKU", Map.of("size", "M", "color", "black"), 100L, "PLN", true);
        assertThat(variant.attributes()).containsEntry("size", "M").containsEntry("color", "black");
    }
}
