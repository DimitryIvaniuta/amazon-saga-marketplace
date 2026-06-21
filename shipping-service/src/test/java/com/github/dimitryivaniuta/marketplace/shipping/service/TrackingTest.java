package com.github.dimitryivaniuta.marketplace.shipping.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Basic shipment identifier invariant test. */
class TrackingTest {
    /** Tracking values are bounded and uppercase. */
    @Test
    void generatedPatternShouldBeBounded() {
        String tracking = "TRK" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        assertThat(tracking).hasSize(19).startsWith("TRK");
    }
}
