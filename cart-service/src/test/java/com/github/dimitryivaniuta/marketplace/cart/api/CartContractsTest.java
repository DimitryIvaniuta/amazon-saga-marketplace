package com.github.dimitryivaniuta.marketplace.cart.api;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests cart mutation contracts. */
class CartContractsTest {
    /** Verifies item identity and quantity are immutable. */
    @Test
    void shouldRepresentCartChange() {
        UUID skuId = UUID.randomUUID();
        var change = new CartContracts.ChangeItem(skuId, 2);
        assertThat(change.skuId()).isEqualTo(skuId);
        assertThat(change.quantity()).isEqualTo(2);
    }
}
