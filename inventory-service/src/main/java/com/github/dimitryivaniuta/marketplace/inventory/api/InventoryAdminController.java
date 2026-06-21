package com.github.dimitryivaniuta.marketplace.inventory.api;

import com.github.dimitryivaniuta.marketplace.inventory.observability.HotSkuTracker;
import com.github.dimitryivaniuta.marketplace.inventory.repository.InventoryRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Administrative inventory API. */
@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
public class InventoryAdminController {

    /** Inventory repository. */
    private final InventoryRepository repository;
    /** Per-replica hot-SKU diagnostics. */
    private final HotSkuTracker hotSkuTracker;

    /** @return current stock counters */
    @GetMapping
    public Mono<List<InventoryRepository.StockView>> stock() {
        return repository.stock().collectList();
    }

    /**
     * Returns bounded per-replica hot-SKU diagnostics without creating
     * high-cardinality Prometheus labels.
     *
     * @param limit maximum rows
     * @return hottest recently accessed SKUs
     */
    @GetMapping("/hot-skus")
    public Mono<List<HotSkuTracker.HotSkuView>> hotSkus(
            @RequestParam(defaultValue = "20") int limit) {
        return Mono.fromSupplier(() -> hotSkuTracker.hottest(limit));
    }

    /** @param request stock update @return completion */
    @PutMapping
    public Mono<Void> set(@Valid @RequestBody StockUpdate request) {
        return repository.setAvailable(request.skuId(), request.availableQuantity());
    }

    /** @param skuId SKU id @param availableQuantity new available units */
    public record StockUpdate(@NotNull UUID skuId, @Min(0) int availableQuantity) { }
}
