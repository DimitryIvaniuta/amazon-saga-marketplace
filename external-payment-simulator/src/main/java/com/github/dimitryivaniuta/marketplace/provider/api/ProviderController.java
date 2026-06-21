package com.github.dimitryivaniuta.marketplace.provider.api;

import com.github.dimitryivaniuta.marketplace.common.web.ApiException;
import com.github.dimitryivaniuta.marketplace.provider.service.ProviderService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Test external-provider HTTP API. */
@RestController
@RequestMapping("/provider/payments")
@RequiredArgsConstructor
public class ProviderController {
    /** Provider use cases. */
    private final ProviderService providerService;

    /** @param key idempotency key @param request authorization @return provider result */
    @PostMapping("/authorize")
    public Mono<ProviderContracts.ProviderResponse> authorize(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ProviderContracts.AuthorizeRequest request) {
        return providerService.authorize(required(key), request);
    }

    /** @param paymentId provider id @param key idempotency key @return result */
    @PostMapping("/{paymentId}/capture")
    public Mono<ProviderContracts.ProviderResponse> capture(
            @PathVariable UUID paymentId, @RequestHeader("Idempotency-Key") String key) {
        return providerService.capture(required(key), paymentId);
    }

    /** @param paymentId provider id @param key idempotency key @return result */
    @PostMapping("/{paymentId}/cancel")
    public Mono<ProviderContracts.ProviderResponse> cancel(
            @PathVariable UUID paymentId, @RequestHeader("Idempotency-Key") String key) {
        return providerService.cancel(required(key), paymentId);
    }

    /** @param paymentId provider id @param key idempotency key @return result */
    @PostMapping("/{paymentId}/refund")
    public Mono<ProviderContracts.ProviderResponse> refund(
            @PathVariable UUID paymentId, @RequestHeader("Idempotency-Key") String key) {
        return providerService.refund(required(key), paymentId);
    }

    /** @param authorizationKey authorization key @return current payment state */
    @GetMapping
    public Mono<ProviderContracts.ProviderResponse> find(
            @RequestParam String authorizationKey) {
        return providerService.find(authorizationKey)
                .switchIfEmpty(notFound());
    }

    /** @param paymentId provider payment id @return current payment state */
    @GetMapping("/{paymentId}")
    public Mono<ProviderContracts.ProviderResponse> findById(@PathVariable UUID paymentId) {
        return providerService.findById(paymentId).switchIfEmpty(notFound());
    }

    private <T> Mono<T> notFound() {
        return Mono.error(new ApiException(HttpStatus.NOT_FOUND,
                "PROVIDER_PAYMENT_NOT_FOUND", "Provider payment was not found"));
    }

    private String required(String key) {
        if (key == null || key.isBlank() || key.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid Idempotency-Key is required");
        }
        return key;
    }
}
