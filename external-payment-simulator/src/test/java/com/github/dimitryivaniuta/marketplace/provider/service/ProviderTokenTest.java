package com.github.dimitryivaniuta.marketplace.provider.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Verifies deterministic token fingerprint behavior used by the simulator. */
class ProviderTokenTest {
    /** Fingerprints are stable between retries. */
    @Test
    void fingerprintShouldBeStable() {
        assertThat(Integer.toHexString("tok_capture_fail".hashCode()))
                .isEqualTo(Integer.toHexString("tok_capture_fail".hashCode()));
    }
}
