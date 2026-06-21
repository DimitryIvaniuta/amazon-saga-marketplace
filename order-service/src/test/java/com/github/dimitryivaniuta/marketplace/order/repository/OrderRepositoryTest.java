package com.github.dimitryivaniuta.marketplace.order.repository;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/** Tests deterministic checkout request hashing. */
class OrderRepositoryTest {
    /** Verifies the same request always produces the same SHA-256 hash. */
    @Test
    void shouldHashDeterministically() {
        assertThat(OrderRepository.hash("request")).isEqualTo(OrderRepository.hash("request")).hasSize(64);
    }
}
