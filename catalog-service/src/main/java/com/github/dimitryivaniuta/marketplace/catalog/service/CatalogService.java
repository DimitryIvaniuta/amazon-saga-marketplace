package com.github.dimitryivaniuta.marketplace.catalog.service;

import com.github.dimitryivaniuta.marketplace.catalog.api.CatalogContracts;
import com.github.dimitryivaniuta.marketplace.catalog.cache.HotProductCache;
import com.github.dimitryivaniuta.marketplace.catalog.repository.CatalogRepository;
import com.github.dimitryivaniuta.marketplace.common.web.ApiException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Catalog query and administration use cases with hot-product cache protection. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    /** Catalog repository. */
    private final CatalogRepository repository;
    /** Two-tier popular-product cache. */
    private final HotProductCache cache;
    /** Reactive transaction operator. */
    private final TransactionalOperator transactionalOperator;

    /** @return active products, served through local and distributed caches */
    public Mono<List<CatalogContracts.Product>> products() {
        return cache.products(() -> repository.findActiveProducts().collectList());
    }

    /** @param id product id @return product */
    public Mono<CatalogContracts.Product> product(UUID id) {
        return cache.product(id, () -> repository.findProduct(id))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product was not found")));
    }

    /** @param id SKU id @return variant */
    public Mono<CatalogContracts.Variant> variant(UUID id) {
        return cache.variant(id, () -> repository.findVariant(id))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "SKU_NOT_FOUND", "SKU was not found")));
    }

    /** @param request product creation data @return created product */
    public Mono<CatalogContracts.Product> create(CatalogContracts.CreateProduct request) {
        UUID productId = UUID.randomUUID();
        return repository.insertProduct(productId, request)
                .thenMany(Flux.fromIterable(request.variants())
                        .concatMap(variant -> repository.insertVariant(
                                productId, UUID.randomUUID(), variant)))
                .then()
                .as(transactionalOperator::transactional)
                .then(cache.invalidateProducts())
                .then(product(productId));
    }
}
