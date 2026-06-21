package com.github.dimitryivaniuta.marketplace.inventory.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Low-cardinality inventory metrics suitable for p95/p99 aggregation. */
@Component
public class InventoryMetrics {

    /** Metrics registry. */
    private final MeterRegistry registry;

    /** @param registry metrics registry */
    public InventoryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Records one SKU-line reservation attempt.
     *
     * @param duration elapsed time
     * @param outcome success, insufficient, contention, or error
     */
    public void reservation(Duration duration, String outcome) {
        Timer.builder("marketplace.inventory.reservation.duration")
                .description("Inventory reservation duration per SKU line")
                .tag("outcome", outcome)
                .register(registry)
                .record(duration.toNanos(), TimeUnit.NANOSECONDS);
        Counter.builder("marketplace.inventory.reservation.attempts")
                .description("Inventory reservation attempts by bounded outcome")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }
}
