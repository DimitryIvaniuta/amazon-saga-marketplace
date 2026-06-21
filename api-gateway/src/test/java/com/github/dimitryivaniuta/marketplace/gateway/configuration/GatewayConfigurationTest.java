package com.github.dimitryivaniuta.marketplace.gateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/** Tests stable low-cardinality gateway rate-limit keys. */
class GatewayConfigurationTest {

    /** Product requests are grouped by the exact normalized product identifier. */
    @Test
    void shouldResolveCatalogProductKey() {
        var configuration = new GatewayConfiguration();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
                "/api/catalog/products/10000000-0000-0000-0000-000000000001").build());

        String key = configuration.catalogResourceKeyResolver().resolve(exchange).block();

        assertThat(key).isEqualTo(
                "catalog:products:10000000-0000-0000-0000-000000000001");
    }

    /** Collection and non-resource catalog routes share one bounded fallback bucket. */
    @Test
    void shouldResolveCatalogCollectionKey() {
        var configuration = new GatewayConfiguration();
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/catalog/products").build());

        String key = configuration.catalogResourceKeyResolver().resolve(exchange).block();

        assertThat(key).isEqualTo("catalog:collection");
    }

    /** Anonymous clients use the numeric remote address and never reverse DNS. */
    @Test
    void shouldResolveAnonymousClientByAddress() {
        var configuration = new GatewayConfiguration();
        var request = MockServerHttpRequest.get("/api/catalog/products")
                .remoteAddress(new InetSocketAddress("192.0.2.42", 41234))
                .build();
        var exchange = MockServerWebExchange.from(request);

        String key = configuration.principalKeyResolver().resolve(exchange).block();

        assertThat(key).isEqualTo("anonymous:192.0.2.42");
    }

    /** All catalog traffic shares one route-capacity key. */
    @Test
    void shouldResolveCatalogRouteKey() {
        var configuration = new GatewayConfiguration();
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/catalog/products/" +
                        "10000000-0000-0000-0000-000000000001").build());

        String key = configuration.catalogRouteKeyResolver().resolve(exchange).block();

        assertThat(key).isEqualTo("route:catalog");
    }
}
