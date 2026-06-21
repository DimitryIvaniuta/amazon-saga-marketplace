package com.github.dimitryivaniuta.marketplace.order.service;

import tools.jackson.databind.json.JsonMapper;
import com.github.dimitryivaniuta.marketplace.common.event.EventEnvelope;
import com.github.dimitryivaniuta.marketplace.common.event.EventTypes;
import com.github.dimitryivaniuta.marketplace.common.event.Topics;
import com.github.dimitryivaniuta.marketplace.common.event.WorkflowPayloads;
import com.github.dimitryivaniuta.marketplace.common.messaging.EventStore;
import com.github.dimitryivaniuta.marketplace.common.messaging.InboxRepository;
import com.github.dimitryivaniuta.marketplace.order.api.OrderContracts;
import com.github.dimitryivaniuta.marketplace.order.domain.SagaStateMachine;
import com.github.dimitryivaniuta.marketplace.order.observability.OrderSagaMetrics;
import com.github.dimitryivaniuta.marketplace.order.repository.OrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/** Orchestrates the long-running checkout Saga and emits compensating commands. */
@Service
@RequiredArgsConstructor
public class OrderSagaService {

    /** Logical inbox consumer. */
    private static final String CONSUMER = "order-saga-service";
    /** Terminal states exposed as a bounded metric tag. */
    private static final Set<String> TERMINAL_STATES = Set.of(
            "COMPLETED", "CANCELLED", "MANUAL_INTERVENTION");
    /** Event store. */
    private final EventStore eventStore;
    /** Inbox repository. */
    private final InboxRepository inboxRepository;
    /** JSON mapper. */
    private final JsonMapper objectMapper;
    /** Order repository. */
    private final OrderRepository repository;
    /** Transaction operator. */
    private final TransactionalOperator transactionalOperator;
    /** End-to-end Saga latency metrics. */
    private final OrderSagaMetrics sagaMetrics;

    /** @param event participant event @return completion */
    public Mono<Void> handle(EventEnvelope event) {
        UUID orderId = orderId(event);
        return inboxRepository.tryStart(event.eventId(), CONSUMER)
                .flatMap(first -> first ? apply(event, orderId) : Mono.empty())
                .as(transactionalOperator::transactional);
    }

    private Mono<Void> apply(EventEnvelope event, UUID orderId) {
        return repository.saga(orderId).flatMap(saga -> switch (event.eventType()) {
            case EventTypes.INVENTORY_RESERVED -> transition(saga.state(), orderId,
                    "WAITING_PAYMENT_AUTHORIZATION", () -> authorize(event, orderId));
            case EventTypes.INVENTORY_REJECTED -> transition(saga.state(), orderId, "CANCELLED",
                    () -> repository.updateOrder(orderId, "CANCELLED", event.eventType()));
            case EventTypes.INVENTORY_RESERVATION_EXPIRED ->
                    reservationExpired(saga.state(), event, orderId);
            case EventTypes.PAYMENT_AUTHORIZED ->
                    paymentAuthorized(saga.state(), event, orderId);
            case EventTypes.PAYMENT_AUTHORIZATION_FAILED ->
                    paymentAuthorizationFailed(saga.state(), event, orderId);
            case EventTypes.INVENTORY_COMMITTED -> transition(saga.state(), orderId,
                    "WAITING_PAYMENT_CAPTURE", () -> commandReference(event, orderId,
                            Topics.PAYMENT_COMMANDS, EventTypes.PAYMENT_CAPTURE_REQUESTED));
            case EventTypes.PAYMENT_CAPTURED -> transition(saga.state(), orderId,
                    "WAITING_SHIPPING", () -> repository.updateOrder(orderId, "PAID", null)
                            .then(createShipping(event, orderId)));
            case EventTypes.PAYMENT_CAPTURE_FAILED -> transition(saga.state(), orderId,
                    "COMPENSATING_CAPTURE", () -> repository.updateOrder(orderId, "CANCELLING", event.eventType())
                            .then(commandReference(event, orderId, Topics.INVENTORY_COMMANDS,
                                    EventTypes.INVENTORY_RESTOCK_REQUESTED))
                            .then(commandReference(event, orderId, Topics.PAYMENT_COMMANDS,
                                    EventTypes.PAYMENT_CANCEL_REQUESTED)));
            case EventTypes.SHIPPING_CREATED -> transition(saga.state(), orderId,
                    "COMPLETED", () -> repository.updateOrder(orderId, "COMPLETED", null));
            case EventTypes.SHIPPING_FAILED -> transition(saga.state(), orderId,
                    "COMPENSATING_FULFILLMENT", () -> repository.updateOrder(orderId, "CANCELLING", event.eventType())
                            .then(commandReference(event, orderId, Topics.PAYMENT_COMMANDS,
                                    EventTypes.PAYMENT_REFUND_REQUESTED))
                            .then(commandReference(event, orderId, Topics.INVENTORY_COMMANDS,
                                    EventTypes.INVENTORY_RESTOCK_REQUESTED)));
            case EventTypes.INVENTORY_RELEASED -> repository.markCompensation(orderId, true, null)
                    .then(completeCompensation(orderId));
            case EventTypes.INVENTORY_RESTOCKED -> repository.markCompensation(orderId, true, null)
                    .then(completeCompensation(orderId));
            case EventTypes.PAYMENT_CANCELLED, EventTypes.PAYMENT_REFUNDED ->
                    repository.markCompensation(orderId, null, true).then(completeCompensation(orderId));
            case EventTypes.PAYMENT_COMPENSATION_FAILED -> transition(saga.state(), orderId,
                    "MANUAL_INTERVENTION", () -> repository.updateOrder(
                            orderId, "MANUAL_INTERVENTION", event.eventType()));
            default -> Mono.error(new IllegalArgumentException("Unsupported Saga event " + event.eventType()));
        });
    }


    private Mono<Void> reservationExpired(String current, EventEnvelope event, UUID orderId) {
        if ("WAITING_PAYMENT_AUTHORIZATION".equals(current)) {
            return transition(current, orderId, "WAITING_PAYMENT_RESOLUTION_AFTER_EXPIRY",
                    () -> repository.updateOrder(orderId, "CANCELLING", event.eventType()));
        }
        if ("WAITING_INVENTORY_COMMIT".equals(current)) {
            return transition(current, orderId, "COMPENSATING_PAYMENT",
                    () -> repository.updateOrder(orderId, "CANCELLING", event.eventType())
                            .then(commandReference(event, orderId, Topics.PAYMENT_COMMANDS,
                                    EventTypes.PAYMENT_CANCEL_REQUESTED)));
        }
        return transition(current, orderId, "CANCELLED",
                () -> repository.updateOrder(orderId, "CANCELLED", event.eventType()));
    }

    private Mono<Void> paymentAuthorized(String current, EventEnvelope event, UUID orderId) {
        if ("WAITING_PAYMENT_RESOLUTION_AFTER_EXPIRY".equals(current)) {
            return transition(current, orderId, "COMPENSATING_PAYMENT",
                    () -> repository.clearPaymentToken(orderId)
                            .then(commandReference(event, orderId, Topics.PAYMENT_COMMANDS,
                                    EventTypes.PAYMENT_CANCEL_REQUESTED)));
        }
        return transition(current, orderId, "WAITING_INVENTORY_COMMIT",
                () -> repository.clearPaymentToken(orderId)
                        .then(commandReference(event, orderId, Topics.INVENTORY_COMMANDS,
                                EventTypes.INVENTORY_COMMIT_REQUESTED)));
    }

    private Mono<Void> paymentAuthorizationFailed(String current, EventEnvelope event, UUID orderId) {
        if ("WAITING_PAYMENT_RESOLUTION_AFTER_EXPIRY".equals(current)) {
            return transition(current, orderId, "CANCELLED",
                    () -> repository.clearPaymentToken(orderId)
                            .then(repository.updateOrder(orderId, "CANCELLED", event.eventType())));
        }
        return transition(current, orderId, "COMPENSATING_INVENTORY",
                () -> repository.clearPaymentToken(orderId)
                        .then(repository.updateOrder(orderId, "CANCELLING", event.eventType()))
                        .then(commandReference(event, orderId, Topics.INVENTORY_COMMANDS,
                                EventTypes.INVENTORY_RELEASE_REQUESTED)));
    }

    private Mono<Void> transition(String current, UUID orderId, String next, java.util.function.Supplier<Mono<Void>> action) {
        if (current.equals(next)) {
            return Mono.empty();
        }
        SagaStateMachine.requireAllowed(current, next);
        return repository.transitionSaga(orderId, current, next)
                .flatMap(changed -> changed == 1L
                        ? action.get().then(recordTerminalLatency(orderId, next))
                        : Mono.error(new IllegalStateException("Concurrent Saga transition detected")));
    }

    private Mono<Void> authorize(EventEnvelope cause, UUID orderId) {
        return repository.paymentData(orderId).flatMap(data -> eventStore.createAndAppend(
                orderId.toString(), Topics.PAYMENT_COMMANDS, EventTypes.PAYMENT_AUTHORIZE_REQUESTED,
                cause.correlationId(), cause.eventId().toString(), objectMapper.valueToTree(
                        new WorkflowPayloads.PaymentAuthorize(
                                orderId, data.totalMinor(), data.currency(), data.paymentToken())))).then();
    }

    private Mono<Void> createShipping(EventEnvelope cause, UUID orderId) {
        return Mono.zip(repository.shippingData(orderId), repository.lines(orderId).collectList())
                .flatMap(tuple -> {
                    try {
                        OrderContracts.ShippingAddress address = objectMapper.readValue(
                                tuple.getT1().address(), OrderContracts.ShippingAddress.class);
                        WorkflowPayloads.ShippingCreate payload = new WorkflowPayloads.ShippingCreate(
                                orderId, tuple.getT1().userId(), address.recipient(), address.addressLine1(), address.city(),
                                address.postalCode(), address.country(), tuple.getT2());
                        return eventStore.createAndAppend(orderId.toString(), Topics.SHIPPING_COMMANDS,
                                EventTypes.SHIPPING_CREATE_REQUESTED, cause.correlationId(),
                                cause.eventId().toString(), objectMapper.valueToTree(payload)).then();
                    } catch (Exception exception) {
                        return Mono.error(exception);
                    }
                });
    }

    private Mono<Void> commandReference(
            EventEnvelope cause, UUID orderId, String topic, String eventType) {
        return eventStore.createAndAppend(orderId.toString(), topic, eventType,
                cause.correlationId(), cause.eventId().toString(),
                objectMapper.valueToTree(new WorkflowPayloads.OrderReference(orderId))).then();
    }

    private Mono<Void> completeCompensation(UUID orderId) {
        return repository.saga(orderId).flatMap(saga -> {
            if (saga.state().equals("MANUAL_INTERVENTION")) {
                return Mono.empty();
            }
            boolean needsBoth = saga.state().equals("COMPENSATING_CAPTURE")
                    || saga.state().equals("COMPENSATING_FULFILLMENT");
            boolean done = needsBoth
                    ? saga.inventoryCompensated() && saga.paymentCompensated()
                    : saga.inventoryCompensated() || saga.paymentCompensated();
            if (!done) {
                return Mono.empty();
            }
            SagaStateMachine.requireAllowed(saga.state(), "CANCELLED");
            return repository.transitionSaga(orderId, saga.state(), "CANCELLED")
                    .flatMap(changed -> changed == 1L
                            ? repository.updateOrder(orderId, "CANCELLED", null)
                                    .then(recordTerminalLatency(orderId, "CANCELLED"))
                            : Mono.error(new IllegalStateException(
                                    "Concurrent Saga compensation transition detected")));
        });
    }

    private Mono<Void> recordTerminalLatency(UUID orderId, String state) {
        if (!TERMINAL_STATES.contains(state)) {
            return Mono.empty();
        }
        return repository.createdAt(orderId)
                .doOnNext(createdAt -> {
                    Duration elapsed = Duration.between(createdAt, Instant.now());
                    sagaMetrics.terminal(elapsed.isNegative() ? Duration.ZERO : elapsed,
                            state.toLowerCase(Locale.ROOT));
                })
                .then();
    }

    private UUID orderId(EventEnvelope event) {
        try {
            return event.payload().hasNonNull("orderId")
                    ? UUID.fromString(event.payload().get("orderId").asText())
                    : throwMissingOrder();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Event has no valid orderId", exception);
        }
    }

    private UUID throwMissingOrder() {
        throw new IllegalArgumentException("Missing orderId");
    }
}
