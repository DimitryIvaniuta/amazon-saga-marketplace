package com.github.dimitryivaniuta.marketplace.common.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Configures aggregable latency histograms for RED metrics and critical
 * marketplace operations. Prometheus derives p95 and p99 across all replicas
 * from histogram buckets instead of averaging per-instance percentiles.
 */
@AutoConfiguration
@ConditionalOnClass(Meter.class)
public class LatencyMetricsAutoConfiguration {

    /** Maximum normalized URI templates retained by HTTP server metrics. */
    private static final int MAX_HTTP_URI_TAGS = 250;

    /**
     * Enables aggregable percentile histograms for bounded latency meters.
     *
     * @return latency distribution filter
     */
    @Bean
    public MeterFilter marketplaceLatencyDistributionFilter() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(
                    Meter.Id id, DistributionStatisticConfig config) {
                if (!isLatencyMeter(id.getName())) {
                    return config;
                }
                return DistributionStatisticConfig.builder()
                        .percentilesHistogram(true)
                        .build()
                        .merge(config);
            }
        };
    }

    /**
     * Prevents accidental path or exception cardinality explosions from exhausting
     * the metrics backend.
     *
     * @return HTTP URI tag limit
     */
    @Bean
    public MeterFilter httpUriCardinalityLimit() {
        return MeterFilter.maximumAllowableTags(
                "http.server.requests", "uri", MAX_HTTP_URI_TAGS, MeterFilter.deny());
    }

    /**
     * Adds the Spring application name to every meter for cross-service dashboards.
     *
     * @param environment Spring environment
     * @return common application tag
     */
    @Bean
    public MeterFilter applicationMetricTag(Environment environment) {
        String application = environment.getProperty("spring.application.name", "unknown-service");
        return MeterFilter.commonTags(Tags.of("application", application));
    }

    private static boolean isLatencyMeter(String name) {
        return name.equals("http.server.requests")
                || name.equals("http.client.requests")
                || name.equals("spring.kafka.listener")
                || name.startsWith("marketplace.") && name.endsWith(".duration");
    }
}
