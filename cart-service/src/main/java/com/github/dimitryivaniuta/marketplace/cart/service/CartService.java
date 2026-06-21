package com.github.dimitryivaniuta.marketplace.cart.service;

import com.github.dimitryivaniuta.marketplace.cart.api.CartContracts;
import com.github.dimitryivaniuta.marketplace.cart.repository.CartRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/** Implements idempotent cart mutations. */
@Service
@RequiredArgsConstructor
public class CartService {

    /** Cart repository. */
    private final CartRepository repository;
    /** Reactive transaction operator. */
    private final TransactionalOperator transactionalOperator;

    /** @param userId owner @return current cart */
    public Mono<CartContracts.Cart> get(UUID userId) {
        return repository.ensureCart(userId).then(repository.find(userId));
    }

    /** @param userId owner @param request item update @return updated cart */
    public Mono<CartContracts.Cart> put(UUID userId, CartContracts.ChangeItem request) {
        return repository.ensureCart(userId)
                .flatMap(cartId -> repository.upsertItem(cartId, request.skuId(), request.quantity()))
                .as(transactionalOperator::transactional)
                .then(repository.find(userId));
    }

    /** @param userId owner @param skuId SKU @return updated cart */
    public Mono<CartContracts.Cart> remove(UUID userId, UUID skuId) {
        return repository.ensureCart(userId)
                .flatMap(cartId -> repository.deleteItem(cartId, skuId))
                .as(transactionalOperator::transactional)
                .then(repository.find(userId));
    }

    /** @param userId owner @return emptied cart */
    public Mono<CartContracts.Cart> clear(UUID userId) {
        return repository.ensureCart(userId)
                .flatMap(repository::clear)
                .as(transactionalOperator::transactional)
                .then(repository.find(userId));
    }
}
