package com.github.dimitryivaniuta.marketplace.payment.service;

import tools.jackson.databind.json.JsonMapper;
import com.github.dimitryivaniuta.marketplace.common.event.EventEnvelope;
import com.github.dimitryivaniuta.marketplace.common.event.EventTypes;
import com.github.dimitryivaniuta.marketplace.common.event.Topics;
import com.github.dimitryivaniuta.marketplace.common.event.WorkflowPayloads;
import com.github.dimitryivaniuta.marketplace.common.messaging.EventStore;
import com.github.dimitryivaniuta.marketplace.common.messaging.InboxRepository;
import com.github.dimitryivaniuta.marketplace.payment.client.PaymentProviderClient;
import com.github.dimitryivaniuta.marketplace.payment.domain.PaymentOperation;
import com.github.dimitryivaniuta.marketplace.payment.domain.ProviderAmbiguousException;
import com.github.dimitryivaniuta.marketplace.payment.domain.ProviderDeclinedException;
import com.github.dimitryivaniuta.marketplace.payment.repository.PaymentRepository;
import com.github.dimitryivaniuta.marketplace.payment.repository.PaymentRepository.PaymentRow;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/** Executes durable, idempotent provider operations for Saga payment commands. */
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** Inbox consumer name. */
    private static final String CONSUMER = "payment-service-v1";
    /** Event store. */
    private final EventStore eventStore;
    /** Inbox repository. */
    private final InboxRepository inboxRepository;
    /** Independent provider-outcome logger. */
    private final IndependentPaymentLogService independentPaymentLogService;
    /** JSON mapper. */
    private final JsonMapper objectMapper;
    /** Provider adapter. */
    private final PaymentProviderClient providerClient;
    /** Payment repository. */
    private final PaymentRepository repository;
    /** Transaction operator. */
    private final TransactionalOperator transactionalOperator;

    /**
     * Persists the inbox claim and attempt before crossing the network boundary.
     * Provider I/O runs only after that local transaction commits.
     *
     * @param event payment command
     * @return processing completion
     */
    public Mono<Void> handle(EventEnvelope event) {
        Mono<PreparedOperation> durablePreparation = inboxRepository.tryStart(event.eventId(), CONSUMER)
                .flatMap(first -> first ? prepare(event) : Mono.empty())
                .as(transactionalOperator::transactional);
        return durablePreparation.flatMap(work -> execute(work, event));
    }

    private Mono<PreparedOperation> prepare(EventEnvelope event) {
        return switch (event.eventType()) {
            case EventTypes.PAYMENT_AUTHORIZE_REQUESTED -> prepareAuthorization(event);
            case EventTypes.PAYMENT_CAPTURE_REQUESTED -> prepareExisting(event, PaymentOperation.CAPTURE);
            case EventTypes.PAYMENT_CANCEL_REQUESTED -> prepareExisting(event, PaymentOperation.CANCEL);
            case EventTypes.PAYMENT_REFUND_REQUESTED -> prepareExisting(event, PaymentOperation.REFUND);
            default -> Mono.error(new IllegalArgumentException("Unsupported payment command " + event.eventType()));
        };
    }

    private Mono<PreparedOperation> prepareAuthorization(EventEnvelope event) {
        WorkflowPayloads.PaymentAuthorize command = objectMapper.convertValue(
                event.payload(), WorkflowPayloads.PaymentAuthorize.class);
        return repository.createOrLoad(command.orderId(), command.amountMinor(), command.currency(), command.paymentToken())
                .flatMap(payment -> {
                    if ("AUTHORIZED".equals(payment.status()) || "CAPTURED".equals(payment.status())) {
                        return Mono.just(PreparedOperation.replay(payment, PaymentOperation.AUTHORIZE));
                    }
                    String key = operationKey(command.orderId(), PaymentOperation.AUTHORIZE);
                    return repository.startAttempt(payment.id(), PaymentOperation.AUTHORIZE, key)
                            .flatMap(attempt -> repository.transition(payment.id(), payment.status(), "AUTHORIZATION_UNKNOWN")
                                    .filter(changed -> changed == 1L)
                                    .switchIfEmpty(Mono.error(new IllegalStateException(
                                            "Concurrent payment authorization transition")))
                                    .thenReturn(new PreparedOperation(payment, attempt,
                                            PaymentOperation.AUTHORIZE, key, false)));
                });
    }

    private Mono<PreparedOperation> prepareExisting(EventEnvelope event, PaymentOperation operation) {
        WorkflowPayloads.OrderReference command = objectMapper.convertValue(
                event.payload(), WorkflowPayloads.OrderReference.class);
        return repository.findByOrderId(command.orderId()).flatMap(payment -> {
            String successState = successState(operation);
            if (successState.equals(payment.status())) {
                return Mono.just(PreparedOperation.replay(payment, operation));
            }
            if (payment.providerPaymentId() == null) {
                return Mono.error(new IllegalStateException("Payment has no provider reference"));
            }
            String key = operationKey(payment.orderId(), operation);
            return repository.startAttempt(payment.id(), operation, key)
                    .flatMap(attempt -> repository.transition(
                                    payment.id(), payment.status(), operation.name() + "_UNKNOWN")
                            .filter(changed -> changed == 1L)
                            .switchIfEmpty(Mono.error(new IllegalStateException(
                                    "Concurrent payment operation transition")))
                            .thenReturn(new PreparedOperation(payment, attempt, operation, key, false)));
        });
    }

    private Mono<Void> execute(PreparedOperation work, EventEnvelope command) {
        if (work.replay()) {
            return emitResult(work.payment(), command, successEvent(work.operation()))
                    .as(transactionalOperator::transactional);
        }
        return work.operation() == PaymentOperation.AUTHORIZE
                ? authorize(work, command)
                : executeExisting(work, command);
    }

    private Mono<Void> authorize(PreparedOperation work, EventEnvelope command) {
        PaymentRow payment = work.payment();
        PaymentProviderClient.AuthorizeRequest request = new PaymentProviderClient.AuthorizeRequest(
                payment.orderId(), payment.amountMinor(), payment.currency(), payment.paymentToken());
        return providerClient.authorize(request, work.key())
                .flatMap(response -> independentPaymentLogService.record(
                                work.attemptId(), "SUCCEEDED", response.reference(), null)
                        .then(repository.authorizationSucceeded(
                                        payment.id(), work.attemptId(), response.paymentId(),
                                        response.reference(), work.key())
                                .then(emitResult(payment.orderId(), response.reference(), command,
                                        EventTypes.PAYMENT_AUTHORIZED))
                                .as(transactionalOperator::transactional)))
                .onErrorResume(ProviderDeclinedException.class, error -> independentPaymentLogService.record(
                                work.attemptId(), "FAILED", null, error.getMessage())
                        .then(repository.operationFailed(
                                        payment.id(), work.attemptId(), "AUTHORIZATION_FAILED", error.getMessage())
                                .then(emitFailure(payment.orderId(), command,
                                        EventTypes.PAYMENT_AUTHORIZATION_FAILED,
                                        "PAYMENT_DECLINED", error.getMessage()))
                                .as(transactionalOperator::transactional)))
                .onErrorResume(ProviderAmbiguousException.class, error -> independentPaymentLogService.record(
                                work.attemptId(), "UNKNOWN", null, error.getMessage())
                        .then(repository.operationUnknown(
                                        payment.id(), work.attemptId(),
                                        "AUTHORIZATION_UNKNOWN", error.getMessage())
                                .then(emitAudit(payment.orderId(), "AUTHORIZE", "UNKNOWN", error.getMessage()))
                                .as(transactionalOperator::transactional)));
    }

    private Mono<Void> executeExisting(PreparedOperation work, EventEnvelope command) {
        PaymentRow payment = work.payment();
        Mono<PaymentProviderClient.ProviderResponse> call = switch (work.operation()) {
            case CAPTURE -> providerClient.capture(payment.providerPaymentId(), work.key());
            case CANCEL -> providerClient.cancel(payment.providerPaymentId(), work.key());
            case REFUND -> providerClient.refund(payment.providerPaymentId(), work.key());
            default -> Mono.error(new IllegalArgumentException("Unsupported operation"));
        };
        return call.flatMap(response -> independentPaymentLogService.record(
                                work.attemptId(), "SUCCEEDED", response.reference(), null)
                        .then(repository.operationSucceeded(
                                        payment.id(), work.attemptId(), successState(work.operation()),
                                        response.reference())
                                .then(emitResult(payment.orderId(), response.reference(), command,
                                        successEvent(work.operation())))
                                .as(transactionalOperator::transactional)))
                .onErrorResume(ProviderDeclinedException.class,
                        error -> definitiveFailure(work, command, error))
                .onErrorResume(ProviderAmbiguousException.class, error -> independentPaymentLogService.record(
                                work.attemptId(), "UNKNOWN", null, error.getMessage())
                        .then(repository.operationUnknown(
                                        payment.id(), work.attemptId(),
                                        work.operation().name() + "_UNKNOWN", error.getMessage())
                                .then(emitAudit(payment.orderId(), work.operation().name(),
                                        "UNKNOWN", error.getMessage()))
                                .as(transactionalOperator::transactional)));
    }

    private Mono<Void> definitiveFailure(
            PreparedOperation work, EventEnvelope command, Throwable error) {
        String eventType = work.operation() == PaymentOperation.CAPTURE
                ? EventTypes.PAYMENT_CAPTURE_FAILED : EventTypes.PAYMENT_COMPENSATION_FAILED;
        return independentPaymentLogService.record(
                        work.attemptId(), "FAILED", null, error.getMessage())
                .then(repository.operationFailed(work.payment().id(), work.attemptId(),
                                work.operation().name() + "_FAILED", error.getMessage())
                        .then(emitFailure(work.payment().orderId(), command, eventType,
                                "PAYMENT_" + work.operation().name() + "_FAILED", error.getMessage()))
                        .as(transactionalOperator::transactional));
    }

    /**
     * Replays an operation with its original provider idempotency key after an
     * ambiguous timeout. Provider-side idempotency makes this safe whether the
     * original request committed or not.
     *
     * @param payment payment requiring reconciliation
     * @return reconciliation completion
     */
    public Mono<Void> reconcile(PaymentRow payment) {
        PaymentOperation operation = operationForUnknownState(payment.status());
        String key = operationKey(payment.orderId(), operation);
        return repository.startAttempt(payment.id(), operation, key)
                .flatMap(attemptId -> reconciliationCall(payment, operation, key)
                        .flatMap(response -> reconciliationSucceeded(
                                payment, attemptId, operation, key, response))
                        .onErrorResume(ProviderDeclinedException.class, error ->
                                reconciliationDeclined(payment, attemptId, operation, error))
                        .onErrorResume(ProviderAmbiguousException.class, error ->
                                independentPaymentLogService.record(
                                                attemptId, "UNKNOWN", null, error.getMessage())
                                        .then(emitAudit(payment.orderId(),
                                                operation.name() + "_RECONCILIATION",
                                                "STILL_UNKNOWN", error.getMessage())
                                                .as(transactionalOperator::transactional))))
                .onErrorResume(IllegalArgumentException.class, error -> Mono.empty());
    }

    private Mono<PaymentProviderClient.ProviderResponse> reconciliationCall(
            PaymentRow payment, PaymentOperation operation, String key) {
        return switch (operation) {
            case AUTHORIZE -> providerClient.authorize(
                    new PaymentProviderClient.AuthorizeRequest(
                            payment.orderId(), payment.amountMinor(), payment.currency(), payment.paymentToken()), key);
            case CAPTURE -> providerClient.capture(payment.providerPaymentId(), key);
            case CANCEL -> providerClient.cancel(payment.providerPaymentId(), key);
            case REFUND -> providerClient.refund(payment.providerPaymentId(), key);
        };
    }

    private Mono<Void> reconciliationSucceeded(
            PaymentRow payment, UUID attemptId, PaymentOperation operation, String key,
            PaymentProviderClient.ProviderResponse response) {
        Mono<Void> update = operation == PaymentOperation.AUTHORIZE
                ? repository.authorizationSucceeded(
                        payment.id(), attemptId, response.paymentId(), response.reference(), key)
                : repository.operationSucceeded(
                        payment.id(), attemptId, successState(operation), response.reference());
        return independentPaymentLogService.record(
                        attemptId, "SUCCEEDED", response.reference(), null)
                .then(update.then(emitRecoveredResult(
                                payment.orderId(), response.reference(), successEvent(operation)))
                        .then(emitAudit(payment.orderId(), operation.name() + "_RECONCILIATION",
                                "SUCCEEDED", response.reference()))
                        .as(transactionalOperator::transactional));
    }

    private Mono<Void> reconciliationDeclined(
            PaymentRow payment, UUID attemptId, PaymentOperation operation, Throwable error) {
        String eventType = switch (operation) {
            case AUTHORIZE -> EventTypes.PAYMENT_AUTHORIZATION_FAILED;
            case CAPTURE -> EventTypes.PAYMENT_CAPTURE_FAILED;
            case CANCEL, REFUND -> EventTypes.PAYMENT_COMPENSATION_FAILED;
        };
        return independentPaymentLogService.record(
                        attemptId, "FAILED", null, error.getMessage())
                .then(repository.operationFailed(payment.id(), attemptId,
                                operation.name() + "_FAILED", error.getMessage())
                        .then(emitRecoveredFailure(payment.orderId(), eventType,
                                "PAYMENT_" + operation.name() + "_FAILED", error.getMessage()))
                        .as(transactionalOperator::transactional));
    }

    private PaymentOperation operationForUnknownState(String status) {
        return switch (status) {
            case "AUTHORIZATION_UNKNOWN" -> PaymentOperation.AUTHORIZE;
            case "CAPTURE_UNKNOWN" -> PaymentOperation.CAPTURE;
            case "CANCEL_UNKNOWN" -> PaymentOperation.CANCEL;
            case "REFUND_UNKNOWN" -> PaymentOperation.REFUND;
            default -> throw new IllegalArgumentException("Not an unknown payment state: " + status);
        };
    }

    private Mono<Void> emitRecoveredResult(UUID orderId, String reference, String eventType) {
        WorkflowPayloads.PaymentResult result = new WorkflowPayloads.PaymentResult(orderId, reference);
        return eventStore.createAndAppend(orderId.toString(), Topics.PAYMENT_EVENTS, eventType,
                orderId.toString(), null, objectMapper.valueToTree(result)).then();
    }

    private Mono<Void> emitRecoveredFailure(
            UUID orderId, String eventType, String code, String message) {
        WorkflowPayloads.Failure failure = new WorkflowPayloads.Failure(orderId, code, bounded(message));
        return eventStore.createAndAppend(orderId.toString(), Topics.PAYMENT_EVENTS, eventType,
                orderId.toString(), null, objectMapper.valueToTree(failure)).then()
                .then(emitAudit(orderId, eventType, "FAILED", message));
    }

    private Mono<Void> emitResult(PaymentRow payment, EventEnvelope cause, String eventType) {
        return emitResult(payment.orderId(), payment.providerReference(), cause, eventType);
    }

    private Mono<Void> emitResult(UUID orderId, String reference, EventEnvelope cause, String eventType) {
        WorkflowPayloads.PaymentResult result = new WorkflowPayloads.PaymentResult(orderId, reference);
        return eventStore.createAndAppend(orderId.toString(), Topics.PAYMENT_EVENTS, eventType,
                cause.correlationId(), cause.eventId().toString(), objectMapper.valueToTree(result)).then()
                .then(emitAudit(orderId, eventType, "SUCCEEDED", reference));
    }

    private Mono<Void> emitFailure(
            UUID orderId, EventEnvelope cause, String eventType, String code, String message) {
        WorkflowPayloads.Failure failure = new WorkflowPayloads.Failure(orderId, code, bounded(message));
        return eventStore.createAndAppend(orderId.toString(), Topics.PAYMENT_EVENTS, eventType,
                cause.correlationId(), cause.eventId().toString(), objectMapper.valueToTree(failure)).then()
                .then(emitAudit(orderId, eventType, "FAILED", message));
    }

    private Mono<Void> emitAudit(UUID orderId, String action, String outcome, String details) {
        WorkflowPayloads.Audit audit = new WorkflowPayloads.Audit(
                "payment-service", action, "PAYMENT", orderId.toString(), outcome,
                bounded(details), Instant.now());
        return eventStore.createAndAppend(orderId.toString(), Topics.AUDIT_EVENTS,
                EventTypes.AUDIT_RECORDED, orderId.toString(), null,
                objectMapper.valueToTree(audit)).then();
    }

    private String successEvent(PaymentOperation operation) {
        return switch (operation) {
            case AUTHORIZE -> EventTypes.PAYMENT_AUTHORIZED;
            case CAPTURE -> EventTypes.PAYMENT_CAPTURED;
            case CANCEL -> EventTypes.PAYMENT_CANCELLED;
            case REFUND -> EventTypes.PAYMENT_REFUNDED;
        };
    }

    private String successState(PaymentOperation operation) {
        return switch (operation) {
            case AUTHORIZE -> "AUTHORIZED";
            case CAPTURE -> "CAPTURED";
            case CANCEL -> "CANCELLED";
            case REFUND -> "REFUNDED";
        };
    }

    /** @param orderId order @param operation operation @return stable provider idempotency key */
    public static String operationKey(UUID orderId, PaymentOperation operation) {
        return orderId + ":" + operation.name().toLowerCase(Locale.ROOT) + ":v1";
    }

    private String bounded(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    /** Prepared durable work item crossing the payment-provider boundary. */
    private record PreparedOperation(
            PaymentRow payment, UUID attemptId, PaymentOperation operation,
            String key, boolean replay) {
        /** @param payment existing payment @param operation completed operation @return replay marker */
        private static PreparedOperation replay(PaymentRow payment, PaymentOperation operation) {
            return new PreparedOperation(payment, null, operation, null, true);
        }
    }
}
