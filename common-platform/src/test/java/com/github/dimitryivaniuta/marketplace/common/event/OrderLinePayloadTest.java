package com.github.dimitryivaniuta.marketplace.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests checkout line arithmetic and validation. */
class OrderLinePayloadTest {

    /** Verifies minor-unit arithmetic is exact. */
    @Test
    void shouldCalculateTotal() {
        OrderLinePayload line = new OrderLinePayload(UUID.randomUUID(), 3, 1_999L, "PLN");
        assertThat(line.totalMinor()).isEqualTo(5_997L);
    }

    /** Verifies zero quantity is not accepted. */
    @Test
    void shouldRejectZeroQuantity() {
        assertThatThrownBy(() -> new OrderLinePayload(UUID.randomUUID(), 0, 10L, "PLN"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
