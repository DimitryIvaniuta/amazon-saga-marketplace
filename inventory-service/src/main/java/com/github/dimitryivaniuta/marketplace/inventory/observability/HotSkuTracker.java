package com.github.dimitryivaniuta.marketplace.inventory.observability;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/**
 * Bounded, per-replica diagnostic tracker for identifying hot SKUs without
 * placing unbounded SKU identifiers into Prometheus labels.
 */
@Component
public class HotSkuTracker {

    /** Maximum distinct SKUs retained by one service replica. */
    private static final int MAX_TRACKED_SKUS = 10_000;
    /** Diagnostic rolling inactivity window. */
    private static final Duration WINDOW = Duration.ofMinutes(10);

    /** Bounded counters. */
    private final Cache<UUID, MutableHotSku> counters = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_SKUS)
            .expireAfterAccess(WINDOW)
            .build();

    /**
     * Records one attempt.
     *
     * @param skuId SKU identifier
     * @param outcome bounded outcome
     * @param duration elapsed duration
     */
    public void record(UUID skuId, String outcome, Duration duration) {
        MutableHotSku counter = counters.get(skuId, ignored -> new MutableHotSku());
        counter.attempts.increment();
        counter.totalNanos.add(duration.toNanos());
        switch (outcome) {
            case "contention" -> counter.contentions.increment();
            case "insufficient" -> counter.insufficient.increment();
            default -> { }
        }
    }

    /** @param limit maximum rows @return hottest SKUs on this replica */
    public List<HotSkuView> hottest(int limit) {
        return counters.asMap().entrySet().stream()
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .sorted(Comparator.comparingLong(HotSkuView::attempts).reversed())
                .limit(Math.max(1, Math.min(limit, 100)))
                .toList();
    }

    /** Mutable lock-free counters. */
    private static final class MutableHotSku {
        private final LongAdder attempts = new LongAdder();
        private final LongAdder contentions = new LongAdder();
        private final LongAdder insufficient = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();

        private HotSkuView snapshot(UUID skuId) {
            long attemptCount = attempts.sum();
            long averageMicros = attemptCount == 0
                    ? 0
                    : totalNanos.sum() / attemptCount / 1_000L;
            return new HotSkuView(
                    skuId, attemptCount, contentions.sum(), insufficient.sum(), averageMicros);
        }
    }

    /**
     * @param skuId SKU
     * @param attempts attempts in the rolling inactivity window
     * @param contentions transient lock-contention outcomes
     * @param insufficient genuine stock shortages
     * @param averageMicros average local reservation latency
     */
    public record HotSkuView(
            UUID skuId,
            long attempts,
            long contentions,
            long insufficient,
            long averageMicros) { }
}
