package com.github.dimitryivaniuta.marketplace.gateway.configuration;

import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Gateway-specific traffic shaping and key-resolution beans. */
@Configuration(proxyBeanMethods = false)
public class GatewayConfiguration {

    /** Public product or SKU path. */
    private static final Pattern CATALOG_RESOURCE = Pattern.compile(
            "^/api/catalog/(products|skus)/([0-9a-fA-F-]{36})$");

    /**
     * Uses an authenticated principal as the client rate-limit key and falls
     * back to a bounded remote-address bucket for public endpoints.
     *
     * @return principal-aware key resolver
     */
    @Bean
    public KeyResolver principalKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(principal -> "client:" + principal.getName())
                .defaultIfEmpty(remoteAddress(exchange.getRequest().getRemoteAddress()));
    }

    /**
     * Places the complete catalog route behind one capacity envelope. This
     * prevents distributed cache-penetration traffic from evading the exact
     * product/SKU limiter by continuously rotating identifiers.
     *
     * @return route-wide catalog key resolver
     */
    @Bean
    public KeyResolver catalogRouteKeyResolver() {
        return exchange -> reactor.core.publisher.Mono.just("route:catalog");
    }

    /**
     * Groups all requests for the same catalog resource into one global burst
     * bucket. This protects a single viral product independently of the number
     * of distinct callers while retaining high limits for normal traffic.
     *
     * @return hot-resource key resolver
     */
    @Bean
    public KeyResolver catalogResourceKeyResolver() {
        return exchange -> {
            String path = exchange.getRequest().getPath().value();
            Matcher matcher = CATALOG_RESOURCE.matcher(path);
            if (matcher.matches()) {
                return reactor.core.publisher.Mono.just(
                        "catalog:" + matcher.group(1) + ':' + matcher.group(2).toLowerCase());
            }
            return reactor.core.publisher.Mono.just("catalog:collection");
        };
    }

    private static String remoteAddress(InetSocketAddress address) {
        if (address == null || address.getAddress() == null) {
            return "anonymous:unknown";
        }
        return "anonymous:" + address.getAddress().getHostAddress();
    }
}
