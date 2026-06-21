package com.github.dimitryivaniuta.marketplace.payment.scheduling;

import com.github.dimitryivaniuta.marketplace.payment.repository.PaymentRepository;
import com.github.dimitryivaniuta.marketplace.payment.service.PaymentService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/** Reconciles every unknown provider outcome using the original idempotency key. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationJob {
    /** Maximum duration of one bounded reconciliation pass. */
    private static final Duration RECONCILIATION_TIMEOUT = Duration.ofSeconds(45);
    /** Payment repository. */
    private final PaymentRepository repository;
    /** Payment service. */
    private final PaymentService paymentService;

    /** Reconciles a bounded batch without overlapping on the same instance. */
    @Scheduled(fixedDelayString = "${marketplace.payment.reconciliation-delay:5s}")
    public void reconcile() {
        try {
            Flux.concat(
                            repository.findForReconciliation("AUTHORIZATION_UNKNOWN"),
                            repository.findForReconciliation("CAPTURE_UNKNOWN"),
                            repository.findForReconciliation("CANCEL_UNKNOWN"),
                            repository.findForReconciliation("REFUND_UNKNOWN"))
                    .concatMap(paymentService::reconcile)
                    .then()
                    .block(RECONCILIATION_TIMEOUT);
        } catch (RuntimeException exception) {
            log.error("Payment reconciliation pass failed", exception);
        }
    }
}
