package com.github.dimitryivaniuta.marketplace.catalog.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Low-cardinality metrics for catalog cache effectiveness and load latency. */
@Component
public class CatalogCacheMetrics {

    /** Metrics registry. */
    private final MeterRegistry registry;

    /** @param registry metrics registry */
    public CatalogCacheMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** @param layer local, redis, or database @param result hit, miss, or error */
    public void request(String layer, String result) {
        Counter.builder("marketplace.catalog.cache.requests")
                .description("Catalog cache requests by bounded layer and result")
                .tag("layer", layer)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /** Records a distributed single-flight lock collision. */
    public void lockContention() {
        registry.counter("marketplace.catalog.cache.lock.contention").increment();
    }

    /** @param duration database load duration @param result success or error */
    public void databaseLoad(Duration duration, String result) {
        Timer.builder("marketplace.catalog.cache.load.duration")
                .description("Catalog database load duration after cache miss")
                .tag("result", result)
                .register(registry)
                .record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }
}
