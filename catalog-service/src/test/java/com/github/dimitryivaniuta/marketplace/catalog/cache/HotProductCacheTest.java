package com.github.dimitryivaniuta.marketplace.catalog.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Unit tests for bounded cache-expiry jitter. */
class HotProductCacheTest {

    /** Jitter remains inside the configured range and never produces a zero TTL. */
    @RepeatedTest(25)
    void shouldApplyBoundedTtlJitter() {
        Duration value = HotProductCache.jitteredTtl(Duration.ofSeconds(100), 0.15);

        assertThat(value).isBetween(Duration.ofSeconds(85), Duration.ofSeconds(115));
    }

    /** A zero ratio preserves the exact configured TTL. */
    @Test
    void shouldKeepBaseTtlWhenJitterDisabled() {
        Duration base = Duration.ofMinutes(5);

        assertThat(HotProductCache.jitteredTtl(base, 0.0)).isEqualTo(base);
    }

    /** Concurrent misses execute one local load and release the flight after completion. */
    @Test
    void shouldCoalesceConcurrentLoadsPerReplica() {
        var flights = new ConcurrentHashMap<String, Mono<?>>();
        var loads = new AtomicInteger();

        var results = Flux.range(0, 32)
                .flatMap(ignored -> HotProductCache.coalesce(flights, "product", () ->
                        Mono.delay(Duration.ofMillis(20))
                                .thenReturn(loads.incrementAndGet())), 32)
                .collectList()
                .block();

        assertThat(results).hasSize(32).containsOnly(1);
        assertThat(loads).hasValue(1);
        Mono.delay(Duration.ofMillis(10)).block();
        assertThat(flights).isEmpty();

        assertThat(HotProductCache.coalesce(flights, "product", () ->
                Mono.just(loads.incrementAndGet())).block()).isEqualTo(2);
        assertThat(loads).hasValue(2);
    }

    /** A disabled negative-cache TTL is rejected at configuration binding time. */
    @Test
    void shouldRejectMissingNegativeCacheTtl() {
        assertThatThrownBy(() -> new CatalogCacheProperties(
                100, Duration.ofSeconds(10), Duration.ofMinutes(5), Duration.ZERO,
                Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofMillis(250),
                Duration.ofMillis(20), 0.15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("negativeTtl must be positive");
    }
}
