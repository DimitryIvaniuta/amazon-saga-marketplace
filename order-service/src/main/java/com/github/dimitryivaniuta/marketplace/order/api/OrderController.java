package com.github.dimitryivaniuta.marketplace.order.api;

import com.github.dimitryivaniuta.marketplace.common.security.AuthenticatedUser;
import com.github.dimitryivaniuta.marketplace.order.service.CheckoutService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Checkout and order-query HTTP API. */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    /** Checkout use cases. */
    private final CheckoutService service;

    /**
     * Starts checkout and returns immediately after the initial transaction/outbox commit.
     * @param idempotencyKey semantic request key
     * @param authorization bearer token forwarded to cart/catalog
     * @param request checkout data
     * @return accepted order
     */
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<OrderContracts.CheckoutAccepted> checkout(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody OrderContracts.Checkout request) {
        return AuthenticatedUser.id().flatMap(userId ->
                service.checkout(userId, idempotencyKey, authorization, request));
    }

    /** @param orderId order @return order state */
    @GetMapping("/{orderId}")
    public Mono<OrderContracts.OrderView> order(@PathVariable UUID orderId) {
        return AuthenticatedUser.id().flatMap(userId -> service.order(orderId, userId));
    }
}
