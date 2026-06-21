package com.github.dimitryivaniuta.marketplace.catalog.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.dimitryivaniuta.marketplace.catalog.api.CatalogContracts;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Two-tier cache for hot catalog resources.
 *
 * <p>Each application replica first uses a bounded Caffeine near-cache to remove
 * repeated network traffic to a hot Redis key. Concurrent misses are coalesced
 * inside the replica. Redis provides cross-replica cache sharing and a short,
 * token-owned lock implements distributed single-flight loading so one expired
 * popular key does not create a database stampede.</p>
 *
 * <p>Authoritative not-found results are cached briefly to protect PostgreSQL
 * against cache-penetration traffic with random product or SKU identifiers.</p>
 */
@Component
public class HotProductCache {

    /** Active product-list key. */
    public static final String PRODUCTS_KEY = "catalog:products:active:v2";
    /** Product cache prefix. */
    private static final String PRODUCT_PREFIX = "catalog:product:v2:";
    /** SKU cache prefix. */
    private static final String SKU_PREFIX = "catalog:sku:v2:";
    /** Lock key suffix. */
    private static final String LOCK_SUFFIX = ":fill-lock";
    /** Redis representation of an authoritative not-found result. */
    private static final String MISSING_SENTINEL = "__marketplace_catalog_missing_v1__";
    /** Local representation of an authoritative not-found result. */
    private static final Object MISSING_LOCAL = new Object();
    /** Safe compare-and-delete script for lock release. */
    private static final RedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);
    /** Product list JSON type. */
    private static final TypeReference<List<CatalogContracts.Product>> PRODUCT_LIST_TYPE = new TypeReference<>() { };
    /** Product JSON type. */
    private static final TypeReference<CatalogContracts.Product> PRODUCT_TYPE = new TypeReference<>() { };
    /** Variant JSON type. */
    private static final TypeReference<CatalogContracts.Variant> VARIANT_TYPE = new TypeReference<>() { };

    /** Local cache. */
    private final Cache<String, Object> localCache;
    /** Per-replica in-flight cache loads keyed by bounded cache key. */
    private final ConcurrentMap<String, Mono<?>> inFlightLoads = new ConcurrentHashMap<>();
    /** Distributed cache client. */
    private final ReactiveStringRedisTemplate redisTemplate;
    /** JSON mapper. */
    private final JsonMapper objectMapper;
    /** Cache settings. */
    private final CatalogCacheProperties properties;
    /** Cache metrics. */
    private final CatalogCacheMetrics metrics;

    /**
     * @param redisTemplate distributed cache client
     * @param objectMapper JSON mapper
     * @param properties cache settings
     * @param metrics cache metrics
     */
    public HotProductCache(
            ReactiveStringRedisTemplate redisTemplate,
            JsonMapper objectMapper,
            CatalogCacheProperties properties,
            CatalogCacheMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(properties.localMaximumSize())
                .expireAfterWrite(properties.localTtl())
                .build();
    }

    /** @param loader authoritative loader @return active products */
    public Mono<List<CatalogContracts.Product>> products(
            Supplier<Mono<List<CatalogContracts.Product>>> loader) {
        return get(PRODUCTS_KEY, PRODUCT_LIST_TYPE, loader);
    }

    /** @param id product id @param loader authoritative loader @return product */
    public Mono<CatalogContracts.Product> product(
            UUID id, Supplier<Mono<CatalogContracts.Product>> loader) {
        return get(PRODUCT_PREFIX + id, PRODUCT_TYPE, loader);
    }

    /** @param id SKU id @param loader authoritative loader @return variant */
    public Mono<CatalogContracts.Variant> variant(
            UUID id, Supplier<Mono<CatalogContracts.Variant>> loader) {
        return get(SKU_PREFIX + id, VARIANT_TYPE, loader);
    }

    /** Invalidates product-list cache after catalog mutation. */
    public Mono<Void> invalidateProducts() {
        localCache.invalidate(PRODUCTS_KEY);
        return redisTemplate.delete(PRODUCTS_KEY).onErrorReturn(0L).then();
    }

    private <T> Mono<T> get(String key, TypeReference<T> type, Supplier<Mono<T>> loader) {
        return Mono.defer(() -> {
            Object local = localCache.getIfPresent(key);
            if (local == MISSING_LOCAL) {
                metrics.request("local", "negative-hit");
                return Mono.empty();
            }
            if (local != null) {
                metrics.request("local", "hit");
                return Mono.just(cast(local));
            }
            metrics.request("local", "miss");
            return coalesce(inFlightLoads, key, () -> readRedis(key, type)
                    .doOnNext(lookup -> {
                        metrics.request("redis", lookup.found() ? "hit" : "negative-hit");
                        localCache.put(key, lookup.found() ? lookup.value() : MISSING_LOCAL);
                    })
                    .onErrorResume(error -> {
                        metrics.request("redis", "error");
                        return Mono.empty();
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        metrics.request("redis", "miss");
                        return loadSingleFlight(key, type, loader);
                    }))
                    .timeout(properties.loadTimeout()))
                    .flatMap(lookup -> lookup.asMono());
        });
    }

    private <T> Mono<CacheLookup<T>> loadSingleFlight(
            String key, TypeReference<T> type, Supplier<Mono<T>> loader) {
        String lockKey = key + LOCK_SUFFIX;
        String token = UUID.randomUUID().toString();
        return redisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, properties.lockTtl())
                .defaultIfEmpty(false)
                .onErrorResume(error -> {
                    metrics.request("redis", "error");
                    return Mono.just(false);
                })
                .flatMap(acquired -> acquired
                        ? loadWithOwnedLock(key, lockKey, token, loader)
                        : waitForWinner(key, type, loader));
    }

    private <T> Mono<CacheLookup<T>> loadWithOwnedLock(
            String key, String lockKey, String token, Supplier<Mono<T>> loader) {
        return Mono.usingWhen(
                Mono.just(token),
                ignored -> loadDatabase(key, loader, true),
                ignored -> release(lockKey, token),
                (ignored, error) -> release(lockKey, token),
                ignored -> release(lockKey, token));
    }

    private <T> Mono<CacheLookup<T>> waitForWinner(
            String key, TypeReference<T> type, Supplier<Mono<T>> loader) {
        metrics.lockContention();
        long deadlineNanos = System.nanoTime() + properties.lockWait().toNanos();
        return pollRedis(key, type, deadlineNanos)
                .doOnNext(lookup -> {
                    metrics.request("redis", lookup.found() ? "hit-after-wait" : "negative-hit");
                    localCache.put(key, lookup.found() ? lookup.value() : MISSING_LOCAL);
                })
                .onErrorResume(error -> {
                    metrics.request("redis", "error");
                    return Mono.empty();
                })
                .switchIfEmpty(loadDatabase(key, loader, false));
    }

    private <T> Mono<CacheLookup<T>> pollRedis(
            String key, TypeReference<T> type, long deadlineNanos) {
        return readRedis(key, type).switchIfEmpty(Mono.defer(() -> {
            if (System.nanoTime() >= deadlineNanos) {
                return Mono.empty();
            }
            return Mono.delay(properties.pollInterval())
                    .then(pollRedis(key, type, deadlineNanos));
        }));
    }

    private <T> Mono<CacheLookup<T>> loadDatabase(
            String key, Supplier<Mono<T>> loader, boolean populateRedis) {
        return timedLoad(loader)
                .map(value -> CacheLookup.found(value))
                .switchIfEmpty(Mono.just(CacheLookup.<T>missing()))
                .flatMap(lookup -> {
                    localCache.put(key, lookup.found() ? lookup.value() : MISSING_LOCAL);
                    if (!populateRedis) {
                        return Mono.just(lookup);
                    }
                    return writeRedis(key, lookup)
                            .onErrorResume(error -> {
                                metrics.request("redis", "error");
                                return Mono.empty();
                            })
                            .thenReturn(lookup);
                });
    }

    private <T> Mono<T> timedLoad(Supplier<Mono<T>> loader) {
        long started = System.nanoTime();
        return loader.get()
                .doOnSuccess(value -> metrics.databaseLoad(
                        Duration.ofNanos(System.nanoTime() - started), "success"))
                .doOnError(error -> metrics.databaseLoad(
                        Duration.ofNanos(System.nanoTime() - started), "error"));
    }

    private <T> Mono<CacheLookup<T>> readRedis(String key, TypeReference<T> type) {
        return redisTemplate.opsForValue().get(key).flatMap(json -> {
            if (MISSING_SENTINEL.equals(json)) {
                return Mono.just(CacheLookup.<T>missing());
            }
            try {
                return Mono.just(CacheLookup.found(objectMapper.readValue(json, type)));
            } catch (Exception exception) {
                return redisTemplate.delete(key).then(Mono.<CacheLookup<T>>empty());
            }
        });
    }

    private Mono<Void> writeRedis(String key, CacheLookup<?> lookup) {
        try {
            String value = lookup.found()
                    ? objectMapper.writeValueAsString(lookup.value())
                    : MISSING_SENTINEL;
            Duration baseTtl = lookup.found() ? properties.redisTtl() : properties.negativeTtl();
            return redisTemplate.opsForValue().set(
                            key, value, jitteredTtl(baseTtl, properties.ttlJitterRatio()))
                    .then();
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }

    private Mono<Void> release(String lockKey, String token) {
        return redisTemplate.execute(RELEASE_LOCK, List.of(lockKey), token)
                .onErrorResume(error -> Mono.empty())
                .then();
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    /**
     * Collapses concurrent loads for one key inside a service replica. This is a
     * second line of defense when Redis is unavailable or the distributed lock
     * wait expires, limiting fallback pressure to at most one load per replica.
     *
     * @param flights shared in-flight map
     * @param key cache key
     * @param supplier cold load supplier
     * @param <T> value type
     * @return shared load publisher
     */
    @SuppressWarnings("unchecked")
    static <T> Mono<T> coalesce(
            ConcurrentMap<String, Mono<?>> flights, String key, Supplier<Mono<T>> supplier) {
        Mono<?> current = flights.get(key);
        if (current != null) {
            return (Mono<T>) current;
        }
        AtomicReference<Mono<T>> self = new AtomicReference<>();
        Mono<T> created = Mono.defer(supplier)
                .doFinally(signal -> flights.remove(key, self.get()))
                .cache();
        self.set(created);
        Mono<?> winner = flights.putIfAbsent(key, created);
        return winner == null ? created : (Mono<T>) winner;
    }

    /**
     * Adds bounded random TTL variation so many popular keys do not expire together.
     *
     * @param base base TTL
     * @param ratio jitter ratio
     * @return jittered TTL
     */
    static Duration jitteredTtl(Duration base, double ratio) {
        if (ratio == 0.0) {
            return base;
        }
        double factor = 1.0 + ThreadLocalRandom.current().nextDouble(-ratio, ratio);
        return Duration.ofMillis(Math.max(1L, Math.round(base.toMillis() * factor)));
    }

    /** Cache lookup that distinguishes an absent key from a cached not-found value. */
    private record CacheLookup<T>(boolean found, T value) {

        /** @param value cached value @param <T> value type @return positive lookup */
        private static <T> CacheLookup<T> found(T value) {
            return new CacheLookup<>(true, value);
        }

        /** @param <T> value type @return negative lookup */
        private static <T> CacheLookup<T> missing() {
            return new CacheLookup<>(false, null);
        }

        /** @return positive value or an empty completion for a cached not-found result */
        private Mono<T> asMono() {
            return found ? Mono.just(value) : Mono.empty();
        }
    }
}
