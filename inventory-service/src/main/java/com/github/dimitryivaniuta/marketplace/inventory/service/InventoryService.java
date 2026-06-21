package com.github.dimitryivaniuta.marketplace.inventory.service;

import tools.jackson.databind.json.JsonMapper;
import com.github.dimitryivaniuta.marketplace.common.event.EventEnvelope;
import com.github.dimitryivaniuta.marketplace.common.event.EventTypes;
import com.github.dimitryivaniuta.marketplace.common.event.Topics;
import com.github.dimitryivaniuta.marketplace.common.event.WorkflowPayloads;
import com.github.dimitryivaniuta.marketplace.common.messaging.EventStore;
import com.github.dimitryivaniuta.marketplace.common.messaging.InboxRepository;
import com.github.dimitryivaniuta.marketplace.inventory.domain.InsufficientInventoryException;
import com.github.dimitryivaniuta.marketplace.inventory.domain.InventoryContentionException;
import com.github.dimitryivaniuta.marketplace.inventory.observability.HotSkuTracker;
import com.github.dimitryivaniuta.marketplace.inventory.observability.InventoryMetrics;
import com.github.dimitryivaniuta.marketplace.inventory.repository.InventoryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Implements atomic inventory reservation and its compensating operations. */
@Service
@RequiredArgsConstructor
public class InventoryService {

    /** Logical inbox consumer name. */
    private static final String CONSUMER = "inventory-service";

    /** Event store. */
    private final EventStore eventStore;
    /** Inbox deduplication repository. */
    private final InboxRepository inboxRepository;
    /** Inventory repository. */
    private final InventoryRepository repository;
    /** JSON mapper. */
    private final JsonMapper objectMapper;
    /** Local transaction operator. */
    private final TransactionalOperator transactionalOperator;
    /** Reservation latency metrics. */
    private final InventoryMetrics inventoryMetrics;
    /** Bounded hot-SKU diagnostics. */
    private final HotSkuTracker hotSkuTracker;

    /**
     * Dispatches a verified command envelope.
     *
     * @param event command
     * @return completion signal
     */
    public Mono<Void> handle(EventEnvelope event) {
        return switch (event.eventType()) {
            case EventTypes.INVENTORY_RESERVE_REQUESTED -> reserve(event);
            case EventTypes.INVENTORY_COMMIT_REQUESTED -> change(event, "RESERVED", "COMMITTED", EventTypes.INVENTORY_COMMITTED, Operation.COMMIT);
            case EventTypes.INVENTORY_RELEASE_REQUESTED -> change(event, "RESERVED", "RELEASED", EventTypes.INVENTORY_RELEASED, Operation.RELEASE);
            case EventTypes.INVENTORY_RESTOCK_REQUESTED -> change(event, "COMMITTED", "RESTOCKED", EventTypes.INVENTORY_RESTOCKED, Operation.RESTOCK);
            default -> Mono.error(new IllegalArgumentException("Unsupported inventory event " + event.eventType()));
        };
    }

    /**
     * Releases expired reservations and emits timeout events.
     *
     * @return completion signal
     */
    public Mono<Void> expireReservations() {
        return repository.expiredReservations().concatMap(this::expireOne).then();
    }

    private Mono<Void> reserve(EventEnvelope event) {
        WorkflowPayloads.InventoryReserve payload = convert(event, WorkflowPayloads.InventoryReserve.class);
        var sortedLines = payload.lines().stream()
                .sorted(Comparator.comparing(line -> line.skuId().toString())).toList();
        Mono<Void> transaction = inboxRepository.tryStart(event.eventId(), CONSUMER)
                .flatMap(first -> first
                        ? repository.createReservation(payload.orderId(), payload.userId(), payload.expiresAt())
                                .thenMany(Flux.fromIterable(sortedLines)
                                        .concatMap(line -> reserveLine(
                                                payload.orderId(), line.skuId(), line.quantity())))
                                .then(eventStore.createAndAppend(
                                        payload.orderId().toString(), Topics.INVENTORY_EVENTS,
                                        EventTypes.INVENTORY_RESERVED, event.correlationId(),
                                        event.eventId().toString(), objectMapper.valueToTree(
                                                new WorkflowPayloads.OrderReference(payload.orderId()))))
                                .then()
                        : Mono.empty())
                .as(transactionalOperator::transactional);
        return transaction.onErrorResume(InsufficientInventoryException.class,
                error -> reject(event, payload.orderId(), error));
    }


    private Mono<Void> reserveLine(UUID orderId, UUID skuId, int quantity) {
        long started = System.nanoTime();
        return repository.reserveLine(orderId, skuId, quantity)
                .flatMap(changed -> {
                    if (changed == 1L) {
                        recordReservation(skuId, started, "success");
                        return Mono.empty();
                    }
                    return repository.availableQuantity(skuId).flatMap(available -> {
                        if (available >= quantity) {
                            recordReservation(skuId, started, "contention");
                            return Mono.error(new InventoryContentionException(skuId));
                        }
                        recordReservation(skuId, started, "insufficient");
                        return Mono.error(new InsufficientInventoryException(skuId));
                    });
                })
                .onErrorMap(error -> {
                    if (error instanceof InventoryContentionException
                            || error instanceof InsufficientInventoryException) {
                        return error;
                    }
                    recordReservation(skuId, started, "error");
                    return error;
                });
    }

    private void recordReservation(UUID skuId, long startedNanos, String outcome) {
        Duration duration = Duration.ofNanos(System.nanoTime() - startedNanos);
        inventoryMetrics.reservation(duration, outcome);
        hotSkuTracker.record(skuId, outcome, duration);
    }

    private Mono<Void> reject(EventEnvelope event, UUID orderId, InsufficientInventoryException error) {
        WorkflowPayloads.Failure failure = new WorkflowPayloads.Failure(
                orderId, "INSUFFICIENT_STOCK", "Requested stock is not available for SKU " + error.getSkuId());
        return inboxRepository.tryStart(event.eventId(), CONSUMER)
                .flatMap(first -> first
                        ? eventStore.createAndAppend(orderId.toString(), Topics.INVENTORY_EVENTS,
                                EventTypes.INVENTORY_REJECTED, event.correlationId(),
                                event.eventId().toString(), objectMapper.valueToTree(failure)).then()
                        : Mono.empty())
                .as(transactionalOperator::transactional);
    }

    private Mono<Void> change(
            EventEnvelope event, String expected, String next, String successType, Operation operation) {
        WorkflowPayloads.OrderReference payload = convert(event, WorkflowPayloads.OrderReference.class);
        UUID orderId = payload.orderId();
        return inboxRepository.tryStart(event.eventId(), CONSUMER)
                .flatMap(first -> first ? repository.reservationStatus(orderId)
                        .switchIfEmpty(Mono.error(new IllegalStateException("Reservation not found")))
                        .flatMap(status -> status.equals(next)
                                ? emitReference(event, orderId, successType)
                                : status.equals(expected)
                                        ? repository.transition(orderId, expected, next)
                                                .flatMap(changed -> changed == 1L
                                                        ? applyLines(orderId, operation)
                                                        : Mono.error(new IllegalStateException("Reservation transition lost")))
                                                .then(emitReference(event, orderId, successType))
                                        : Mono.error(new IllegalStateException(
                                                "Invalid reservation transition " + status + " -> " + next)))
                        : Mono.empty())
                .as(transactionalOperator::transactional);
    }

    private Mono<Void> applyLines(UUID orderId, Operation operation) {
        return repository.lines(orderId).concatMap(line -> switch (operation) {
            case COMMIT -> repository.commitLine(line);
            case RELEASE -> repository.releaseLine(line);
            case RESTOCK -> repository.restockLine(line);
        }).then();
    }

    private Mono<Void> emitReference(EventEnvelope event, UUID orderId, String eventType) {
        return eventStore.createAndAppend(orderId.toString(), Topics.INVENTORY_EVENTS, eventType,
                event.correlationId(), event.eventId().toString(),
                objectMapper.valueToTree(new WorkflowPayloads.OrderReference(orderId))).then();
    }

    private Mono<Void> expireOne(UUID orderId) {
        String correlation = orderId.toString();
        return repository.transition(orderId, "RESERVED", "EXPIRED")
                .flatMap(changed -> changed == 1L
                        ? applyLines(orderId, Operation.RELEASE)
                                .then(eventStore.createAndAppend(orderId.toString(), Topics.INVENTORY_EVENTS,
                                        EventTypes.INVENTORY_RESERVATION_EXPIRED, correlation, null,
                                        objectMapper.valueToTree(new WorkflowPayloads.OrderReference(orderId))))
                                .then()
                        : Mono.empty())
                .as(transactionalOperator::transactional);
    }

    private <T> T convert(EventEnvelope event, Class<T> type) {
        try {
            return objectMapper.treeToValue(event.payload(), type);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid payload for " + event.eventType(), exception);
        }
    }

    private enum Operation { COMMIT, RELEASE, RESTOCK }
}
