package com.github.dimitryivaniuta.marketplace.catalog.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable two-tier cache settings for popular catalog resources.
 *
 * @param localMaximumSize maximum entries retained by each service replica
 * @param localTtl near-cache lifetime
 * @param redisTtl distributed cache lifetime before jitter is applied
 * @param negativeTtl short lifetime for authoritative not-found results
 * @param loadTimeout maximum complete cache read/fill duration
 * @param lockTtl maximum lifetime of a cache-fill ownership lock
 * @param lockWait maximum time a losing reader waits for the winning loader
 * @param pollInterval delay between distributed-cache polls
 * @param ttlJitterRatio fractional positive or negative TTL jitter, from {@code 0.0} to {@code 0.5}
 */
@ConfigurationProperties("marketplace.catalog.cache")
public record CatalogCacheProperties(
        long localMaximumSize,
        Duration localTtl,
        Duration redisTtl,
        Duration negativeTtl,
        Duration loadTimeout,
        Duration lockTtl,
        Duration lockWait,
        Duration pollInterval,
        double ttlJitterRatio) {

    /** Validates values early so an invalid cache configuration cannot reach production. */
    public CatalogCacheProperties {
        if (localMaximumSize < 1) {
            throw new IllegalArgumentException("localMaximumSize must be positive");
        }
        if (localTtl == null || localTtl.isNegative() || localTtl.isZero()) {
            throw new IllegalArgumentException("localTtl must be positive");
        }
        if (redisTtl == null || redisTtl.isNegative() || redisTtl.isZero()) {
            throw new IllegalArgumentException("redisTtl must be positive");
        }
        if (negativeTtl == null || negativeTtl.isNegative() || negativeTtl.isZero()) {
            throw new IllegalArgumentException("negativeTtl must be positive");
        }
        if (loadTimeout == null || loadTimeout.isNegative() || loadTimeout.isZero()) {
            throw new IllegalArgumentException("loadTimeout must be positive");
        }
        if (lockTtl == null || lockTtl.isNegative() || lockTtl.isZero()) {
            throw new IllegalArgumentException("lockTtl must be positive");
        }
        if (lockWait == null || lockWait.isNegative()) {
            throw new IllegalArgumentException("lockWait must not be negative");
        }
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
        if (ttlJitterRatio < 0.0 || ttlJitterRatio > 0.5) {
            throw new IllegalArgumentException("ttlJitterRatio must be between 0.0 and 0.5");
        }
    }
}
