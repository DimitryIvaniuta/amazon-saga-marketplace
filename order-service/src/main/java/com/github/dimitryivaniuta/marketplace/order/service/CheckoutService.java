package com.github.dimitryivaniuta.marketplace.order.service;

import tools.jackson.databind.json.JsonMapper;
import com.github.dimitryivaniuta.marketplace.common.event.EventTypes;
import com.github.dimitryivaniuta.marketplace.common.event.OrderLinePayload;
import com.github.dimitryivaniuta.marketplace.common.event.Topics;
import com.github.dimitryivaniuta.marketplace.common.event.WorkflowPayloads;
import com.github.dimitryivaniuta.marketplace.common.messaging.EventStore;
import com.github.dimitryivaniuta.marketplace.common.web.ApiException;
import com.github.dimitryivaniuta.marketplace.order.api.OrderContracts;
import com.github.dimitryivaniuta.marketplace.order.client.CartCatalogClient;
import com.github.dimitryivaniuta.marketplace.order.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/** Creates immutable orders and starts the purchase Saga idempotently. */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    /** Cart/catalog client. */
    private final CartCatalogClient cartCatalogClient;
    /** Event store. */
    private final EventStore eventStore;
    /** JSON mapper. */
    private final JsonMapper objectMapper;
    /** Order repository. */
    private final OrderRepository repository;
    /** Transaction operator. */
    private final TransactionalOperator transactionalOperator;

    /**
     * Starts checkout. Existing keys are resolved without re-reading a mutable
     * cart. A new key, order snapshot, Saga row, and first outbox command are
     * committed atomically in one local database transaction.
     *
     * @param userId user
     * @param idempotencyKey client key
     * @param bearerToken original token
     * @param request checkout data
     * @return accepted order
     */
    public Mono<OrderContracts.CheckoutAccepted> checkout(
            UUID userId, String idempotencyKey, String bearerToken, OrderContracts.Checkout request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            return Mono.error(new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid Idempotency-Key header is required"));
        }
        String requestHash;
        try {
            requestHash = OrderRepository.hash(objectMapper.writeValueAsString(request));
        } catch (Exception exception) {
            return Mono.error(exception);
        }
        return repository.findClaim(userId, idempotencyKey)
                .flatMap(claim -> validateExisting(claim, requestHash))
                .switchIfEmpty(cartCatalogClient.snapshot(bearerToken)
                        .flatMap(lines -> claimAndCreate(userId, idempotencyKey, requestHash, request, lines)));
    }

    /** @param orderId order @param userId owner @return order */
    public Mono<OrderContracts.OrderView> order(UUID orderId, UUID userId) {
        return repository.view(orderId, userId)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order was not found")));
    }

    private Mono<OrderContracts.CheckoutAccepted> validateExisting(
            OrderRepository.IdempotencyClaim claim, String requestHash) {
        if (!claim.requestHash().equals(requestHash)) {
            return Mono.error(new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key was already used for a different request"));
        }
        return Mono.just(new OrderContracts.CheckoutAccepted(claim.orderId(), "ACCEPTED"));
    }

    private Mono<OrderContracts.CheckoutAccepted> claimAndCreate(
            UUID userId, String key, String requestHash,
            OrderContracts.Checkout request, List<OrderLinePayload> lines) {
        UUID proposedOrderId = UUID.randomUUID();
        return repository.claim(userId, key, requestHash, proposedOrderId)
                .flatMap(claim -> {
                    if (!claim.requestHash().equals(requestHash)) {
                        return Mono.error(new ApiException(HttpStatus.CONFLICT,
                                "IDEMPOTENCY_KEY_REUSED",
                                "Idempotency key was already used for a different request"));
                    }
                    if (!claim.owner()) {
                        return Mono.just(new OrderContracts.CheckoutAccepted(claim.orderId(), "ACCEPTED"));
                    }
                    return createOrder(claim.orderId(), userId, request, lines);
                }).as(transactionalOperator::transactional);
    }

    private Mono<OrderContracts.CheckoutAccepted> createOrder(
            UUID orderId, UUID userId, OrderContracts.Checkout request, List<OrderLinePayload> lines) {
        String currency = lines.getFirst().currency();
        if (lines.stream().anyMatch(line -> !currency.equals(line.currency()))) {
            return Mono.error(new ApiException(HttpStatus.CONFLICT, "MIXED_CURRENCY_CART",
                    "All cart items must use one currency"));
        }
        long total = lines.stream().mapToLong(OrderLinePayload::totalMinor).reduce(0L, Math::addExact);
        String address;
        try {
            address = objectMapper.writeValueAsString(request.shippingAddress());
        } catch (Exception exception) {
            return Mono.error(exception);
        }
        WorkflowPayloads.InventoryReserve reserve = new WorkflowPayloads.InventoryReserve(
                orderId, userId, lines, Instant.now().plusSeconds(15 * 60));
        return repository.insertOrder(orderId, userId, total, currency, request.paymentToken(), address)
                .then(repository.insertLines(orderId, lines))
                .then(repository.insertSaga(orderId))
                .then(eventStore.createAndAppend(orderId.toString(), Topics.INVENTORY_COMMANDS,
                        EventTypes.INVENTORY_RESERVE_REQUESTED, orderId.toString(), null,
                        objectMapper.valueToTree(reserve)))
                .thenReturn(new OrderContracts.CheckoutAccepted(orderId, "ACCEPTED"));
    }
}
