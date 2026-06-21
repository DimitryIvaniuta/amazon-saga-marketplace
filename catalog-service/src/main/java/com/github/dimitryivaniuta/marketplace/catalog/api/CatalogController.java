package com.github.dimitryivaniuta.marketplace.catalog.api;

import com.github.dimitryivaniuta.marketplace.catalog.service.CatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Product and SKU HTTP API. */
@RestController
@RequiredArgsConstructor
public class CatalogController {

    /** Catalog use cases. */
    private final CatalogService service;

    /** @return active catalog */
    @GetMapping("/api/catalog/products")
    public Mono<List<CatalogContracts.Product>> products() {
        return service.products();
    }

    /** @param id product id @return product */
    @GetMapping("/api/catalog/products/{id}")
    public Mono<CatalogContracts.Product> product(@PathVariable UUID id) {
        return service.product(id);
    }

    /** @param id SKU id @return variant */
    @GetMapping("/api/catalog/skus/{id}")
    public Mono<CatalogContracts.Variant> variant(@PathVariable UUID id) {
        return service.variant(id);
    }

    /** @param request product data @return created product */
    @PostMapping("/api/admin/catalog/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CatalogContracts.Product> create(@Valid @RequestBody CatalogContracts.CreateProduct request) {
        return service.create(request);
    }
}
