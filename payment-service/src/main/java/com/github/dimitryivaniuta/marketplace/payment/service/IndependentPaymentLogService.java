package com.github.dimitryivaniuta.marketplace.payment.service;

import com.github.dimitryivaniuta.marketplace.payment.repository.PaymentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Mono;

/**
 * Stores the externally observed result of a payment-provider call in an
 * isolated transaction before the parent business-state transaction runs.
 * This preserves evidence of a charge, decline, or timeout even when a later
 * order-event/outbox update fails and is rolled back.
 */
@Service
public class IndependentPaymentLogService {

    /** Payment persistence gateway. */
    private final PaymentRepository repository;
    /** Requires-new transaction operator. */
    private final TransactionalOperator transactionalOperator;

    /**
     * Creates the independent logger.
     *
     * @param repository payment persistence gateway
     * @param transactionManager reactive database transaction manager
     */
    public IndependentPaymentLogService(
            PaymentRepository repository,
            ReactiveTransactionManager transactionManager) {
        this.repository = repository;
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setName("independent-payment-operation-log");
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionalOperator = TransactionalOperator.create(transactionManager, definition);
    }

    /**
     * Records a provider outcome independently from the parent transaction.
     *
     * @param attemptId durable attempt identifier
     * @param status outcome status
     * @param reference provider reference, when known
     * @param error bounded diagnostic, when present
     * @return completion signal
     */
    public Mono<Void> record(
            UUID attemptId, String status, String reference, String error) {
        return repository.completeAttempt(attemptId, status, reference, error)
                .as(transactionalOperator::transactional);
    }
}
