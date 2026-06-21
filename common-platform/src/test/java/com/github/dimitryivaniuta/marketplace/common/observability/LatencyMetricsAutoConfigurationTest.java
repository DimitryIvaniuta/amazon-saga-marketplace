package com.github.dimitryivaniuta.marketplace.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.junit.jupiter.api.Test;

/** Tests the shared percentile histogram policy. */
class LatencyMetricsAutoConfigurationTest {

    /** Critical marketplace timers publish aggregable histogram buckets. */
    @Test
    void shouldPublishCriticalLatencyHistogram() {
        MeterFilter filter = new LatencyMetricsAutoConfiguration()
                .marketplaceLatencyDistributionFilter();
        for (String meterName : java.util.List.of(
                "http.server.requests",
                "http.client.requests",
                "spring.kafka.listener",
                "marketplace.inventory.reservation.duration")) {
            Meter.Id id = new Meter.Id(
                    meterName, Tags.empty(), null, null, Meter.Type.TIMER);

            DistributionStatisticConfig configured = filter.configure(
                    id, DistributionStatisticConfig.DEFAULT);

            assertThat(configured.isPercentileHistogram())
                    .as("histogram enabled for %s", meterName)
                    .isTrue();
        }
    }
}
