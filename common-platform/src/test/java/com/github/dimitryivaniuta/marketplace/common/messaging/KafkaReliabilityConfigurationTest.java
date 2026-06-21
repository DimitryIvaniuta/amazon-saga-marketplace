package com.github.dimitryivaniuta.marketplace.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests classification of short-lived Kafka listener contention. */
class KafkaReliabilityConfigurationTest {

    /** Wrapped contention is recognized through the full cause chain. */
    @Test
    void shouldRecognizeWrappedTransientContention() {
        var failure = new IllegalStateException(
                "listener failed", new TransientContentionException("hot row"));

        assertThat(KafkaReliabilityConfiguration.isTransientContention(failure)).isTrue();
        assertThat(KafkaReliabilityConfiguration.isTransientContention(
                new IllegalArgumentException("permanent"))).isFalse();
    }
}
