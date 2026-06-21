package com.github.dimitryivaniuta.marketplace.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests inventory failure diagnostics. */
class InsufficientInventoryExceptionTest {
    /** Verifies the failed SKU is retained for compensation diagnostics. */
    @Test
    void shouldExposeSku() {
        UUID sku = UUID.randomUUID();
        assertThat(new InsufficientInventoryException(sku).getSkuId()).isEqualTo(sku);
    }
}
