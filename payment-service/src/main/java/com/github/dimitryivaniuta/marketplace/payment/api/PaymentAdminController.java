package com.github.dimitryivaniuta.marketplace.payment.api;

import com.github.dimitryivaniuta.marketplace.payment.repository.PaymentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Restricted operational payment projection API. */
@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class PaymentAdminController {
    /** Payment repository. */
    private final PaymentRepository repository;

    /** @param orderId order id @return payment projection */
    @GetMapping("/orders/{orderId}")
    public Mono<PaymentRepository.PaymentRow> byOrder(@PathVariable UUID orderId) {
        return repository.findByOrderId(orderId);
    }
}
