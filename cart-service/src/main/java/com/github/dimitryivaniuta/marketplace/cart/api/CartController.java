package com.github.dimitryivaniuta.marketplace.cart.api;

import com.github.dimitryivaniuta.marketplace.cart.service.CartService;
import com.github.dimitryivaniuta.marketplace.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Authenticated user's cart API. */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    /** Cart use cases. */
    private final CartService service;

    /** @return current cart */
    @GetMapping
    public Mono<CartContracts.Cart> get() {
        return AuthenticatedUser.id().flatMap(service::get);
    }

    /** @param request item update @return updated cart */
    @PutMapping("/items")
    public Mono<CartContracts.Cart> put(@Valid @RequestBody CartContracts.ChangeItem request) {
        return AuthenticatedUser.id().flatMap(userId -> service.put(userId, request));
    }

    /** @param skuId SKU id @return updated cart */
    @DeleteMapping("/items/{skuId}")
    public Mono<CartContracts.Cart> remove(@PathVariable UUID skuId) {
        return AuthenticatedUser.id().flatMap(userId -> service.remove(userId, skuId));
    }

    /** @return empty cart */
    @DeleteMapping
    public Mono<CartContracts.Cart> clear() {
        return AuthenticatedUser.id().flatMap(service::clear);
    }
}
