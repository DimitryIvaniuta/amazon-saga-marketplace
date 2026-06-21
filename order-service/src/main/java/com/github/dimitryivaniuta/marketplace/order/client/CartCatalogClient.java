package com.github.dimitryivaniuta.marketplace.order.client;

import com.github.dimitryivaniuta.marketplace.common.event.OrderLinePayload;
import com.github.dimitryivaniuta.marketplace.common.web.ApiException;
import com.github.dimitryivaniuta.marketplace.order.configuration.DownstreamProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Reads the current cart and authoritative SKU prices before creating an order snapshot. */
@Component
@RequiredArgsConstructor
public class CartCatalogClient {

    /** WebClient builder. */
    private final WebClient.Builder webClientBuilder;
    /** Downstream URLs. */
    private final DownstreamProperties properties;

    /**
     * Creates immutable priced lines from the current cart.
     *
     * @param bearerToken original bearer token
     * @return priced lines
     */
    public Mono<List<OrderLinePayload>> snapshot(String bearerToken) {
        WebClient cartClient = webClientBuilder.baseUrl(properties.cartBaseUrl()).build();
        return cartClient.get().uri("/api/cart")
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .retrieve().bodyToMono(CartResponse.class)
                .flatMap(cart -> cart.items().isEmpty()
                        ? Mono.error(new ApiException(HttpStatus.CONFLICT, "CART_EMPTY", "Cart is empty"))
                        : Flux.fromIterable(cart.items())
                                .flatMap(item -> priced(item, bearerToken), 8)
                                .collectList())
                .timeout(properties.timeout());
    }

    private Mono<OrderLinePayload> priced(CartItem item, String bearerToken) {
        WebClient catalogClient = webClientBuilder.baseUrl(properties.catalogBaseUrl()).build();
        return catalogClient.get().uri("/api/catalog/skus/{id}", item.skuId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .retrieve().bodyToMono(VariantResponse.class)
                .flatMap(variant -> variant.active()
                        ? Mono.just(new OrderLinePayload(
                                variant.id(), item.quantity(), variant.priceMinor(), variant.currency()))
                        : Mono.error(new ApiException(HttpStatus.CONFLICT, "SKU_INACTIVE",
                                "Cart contains an inactive SKU: " + item.skuId())))
                .timeout(properties.timeout());
    }

    private record CartResponse(UUID cartId, UUID userId, List<CartItem> items) { }
    private record CartItem(UUID skuId, int quantity) { }
    private record VariantResponse(UUID id, String skuCode, Map<String, String> attributes, long priceMinor, String currency, boolean active) { }
}
