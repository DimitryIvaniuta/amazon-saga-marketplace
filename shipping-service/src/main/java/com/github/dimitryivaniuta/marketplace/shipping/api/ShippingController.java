package com.github.dimitryivaniuta.marketplace.shipping.api;

import com.github.dimitryivaniuta.marketplace.common.security.AuthenticatedUser;
import com.github.dimitryivaniuta.marketplace.common.web.ApiException;
import com.github.dimitryivaniuta.marketplace.shipping.repository.ShippingRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Customer shipment tracking API. */
@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingController {
    /** Shipping repository. */
    private final ShippingRepository repository;

    /** @param orderId order @return owned shipment */
    @GetMapping("/orders/{orderId}")
    public Mono<ShippingRepository.ShipmentRow> byOrder(@PathVariable UUID orderId) {
        return AuthenticatedUser.id().flatMap(userId -> repository.byOrder(orderId)
                .filter(row -> userId.equals(row.userId())))
                .switchIfEmpty(Mono.error(new ApiException(HttpStatus.NOT_FOUND,
                        "SHIPMENT_NOT_FOUND", "Shipment was not found")));
    }
}
