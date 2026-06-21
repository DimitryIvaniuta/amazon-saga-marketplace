package com.github.dimitryivaniuta.marketplace.payment.client;

import com.github.dimitryivaniuta.marketplace.payment.configuration.PaymentProviderProperties;
import com.github.dimitryivaniuta.marketplace.payment.domain.ProviderAmbiguousException;
import com.github.dimitryivaniuta.marketplace.payment.domain.ProviderDeclinedException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** HTTP adapter for the external provider's idempotent payment API. */
@Component
@RequiredArgsConstructor
public class PaymentProviderClient {

    /** Provider client. */
    private final WebClient paymentProviderWebClient;
    /** Provider settings. */
    private final PaymentProviderProperties properties;

    /** @param request authorization request @param key stable operation key @return provider result */
    public Mono<ProviderResponse> authorize(AuthorizeRequest request, String key) {
        return post("/provider/payments/authorize", request, key);
    }

    /** @param providerId provider payment @param key stable operation key @return provider result */
    public Mono<ProviderResponse> capture(UUID providerId, String key) {
        return post("/provider/payments/{id}/capture", new EmptyRequest(), key, providerId);
    }

    /** @param providerId provider payment @param key stable operation key @return provider result */
    public Mono<ProviderResponse> cancel(UUID providerId, String key) {
        return post("/provider/payments/{id}/cancel", new EmptyRequest(), key, providerId);
    }

    /** @param providerId provider payment @param key stable operation key @return provider result */
    public Mono<ProviderResponse> refund(UUID providerId, String key) {
        return post("/provider/payments/{id}/refund", new EmptyRequest(), key, providerId);
    }

    /** @param key authorization operation key @return provider state, empty when unknown */
    public Mono<ProviderResponse> findByAuthorizationKey(String key) {
        return query("/provider/payments?authorizationKey={key}", key);
    }

    /** @param paymentId provider payment identifier @return current provider state */
    public Mono<ProviderResponse> findByPaymentId(UUID paymentId) {
        return query("/provider/payments/{id}", paymentId);
    }

    private Mono<ProviderResponse> query(String uri, Object variable) {
        return paymentProviderWebClient.get().uri(uri, variable)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == 404) {
                        return Mono.empty();
                    }
                    if (response.statusCode().isError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("Provider query failed")
                                .flatMap(body -> Mono.error(new ProviderAmbiguousException(body, null)));
                    }
                    return response.bodyToMono(ProviderResponse.class);
                })
                .timeout(properties.timeout())
                .onErrorMap(error -> error instanceof ProviderAmbiguousException ? error
                        : new ProviderAmbiguousException("Provider query outcome is unknown", error));
    }

    private Mono<ProviderResponse> post(String uri, Object body, String key, Object... variables) {
        return paymentProviderWebClient.post().uri(uri, variables)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("Idempotency-Key", key)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("Payment was declined")
                        .flatMap(message -> Mono.error(new ProviderDeclinedException(message))))
                .onStatus(HttpStatusCode::is5xxServerError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("Provider server error")
                        .flatMap(message -> Mono.error(new ProviderAmbiguousException(message, null))))
                .bodyToMono(ProviderResponse.class)
                .timeout(properties.timeout())
                .onErrorMap(error -> !(error instanceof ProviderDeclinedException)
                                && !(error instanceof ProviderAmbiguousException),
                        error -> new ProviderAmbiguousException(
                                "Provider operation outcome is unknown", error));
    }

    /** @param orderId order @param amountMinor amount @param currency ISO currency @param paymentToken opaque token */
    public record AuthorizeRequest(UUID orderId, long amountMinor, String currency, String paymentToken) { }
    /** Empty provider operation body. */
    public record EmptyRequest() { }
    /** @param paymentId provider payment @param status state @param reference public reference */
    public record ProviderResponse(UUID paymentId, String status, String reference) { }
}
