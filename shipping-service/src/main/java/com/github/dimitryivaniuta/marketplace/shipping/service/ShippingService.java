package com.github.dimitryivaniuta.marketplace.shipping.service;

import tools.jackson.databind.json.JsonMapper;
import com.github.dimitryivaniuta.marketplace.common.event.EventEnvelope;
import com.github.dimitryivaniuta.marketplace.common.event.EventTypes;
import com.github.dimitryivaniuta.marketplace.common.event.Topics;
import com.github.dimitryivaniuta.marketplace.common.event.WorkflowPayloads;
import com.github.dimitryivaniuta.marketplace.common.messaging.EventStore;
import com.github.dimitryivaniuta.marketplace.common.messaging.InboxRepository;
import com.github.dimitryivaniuta.marketplace.shipping.repository.ShippingRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/** Creates shipments idempotently and reports fulfillment outcomes to the Saga. */
@Service
@RequiredArgsConstructor
public class ShippingService {
    /** Inbox consumer name. */
    private static final String CONSUMER = "shipping-service-v1";
    /** Event store. */
    private final EventStore eventStore;
    /** Inbox repository. */
    private final InboxRepository inboxRepository;
    /** JSON mapper. */
    private final JsonMapper objectMapper;
    /** Shipping repository. */
    private final ShippingRepository repository;
    /** Transaction operator. */
    private final TransactionalOperator transactionalOperator;

    /** @param event shipping command @return completion */
    public Mono<Void> handle(EventEnvelope event) {
        WorkflowPayloads.ShippingCreate command = objectMapper.convertValue(
                event.payload(), WorkflowPayloads.ShippingCreate.class);
        return inboxRepository.tryStart(event.eventId(), CONSUMER).flatMap(first -> {
            if (!first) {
                return Mono.empty();
            }
            if (command.addressLine1().toUpperCase(Locale.ROOT).contains("FAIL_SHIPPING")) {
                WorkflowPayloads.Failure failure = new WorkflowPayloads.Failure(
                        command.orderId(), "SHIPPING_PROVIDER_REJECTED", "Test shipping rejection");
                return emit(command.orderId(), event, EventTypes.SHIPPING_FAILED, failure)
                        .then(audit(command.orderId(), "CREATE_SHIPMENT", "FAILED"));
            }
            UUID shipmentId = UUID.randomUUID();
            String tracking = "TRK" + shipmentId.toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
            return repository.create(command, shipmentId, tracking)
                    .flatMap(row -> emit(command.orderId(), event, EventTypes.SHIPPING_CREATED,
                            new WorkflowPayloads.ShippingResult(
                                    command.orderId(), row.id(), row.trackingNumber())))
                    .then(audit(command.orderId(), "CREATE_SHIPMENT", "SUCCEEDED"));
        }).as(transactionalOperator::transactional);
    }

    private Mono<Void> emit(UUID orderId, EventEnvelope cause, String eventType, Object payload) {
        return eventStore.createAndAppend(orderId.toString(), Topics.SHIPPING_EVENTS, eventType,
                cause.correlationId(), cause.eventId().toString(), objectMapper.valueToTree(payload)).then();
    }

    private Mono<Void> audit(UUID orderId, String action, String outcome) {
        WorkflowPayloads.Audit audit = new WorkflowPayloads.Audit(
                "shipping-service", action, "SHIPMENT", orderId.toString(), outcome, "", Instant.now());
        return eventStore.createAndAppend(orderId.toString(), Topics.AUDIT_EVENTS,
                EventTypes.AUDIT_RECORDED, orderId.toString(), null,
                objectMapper.valueToTree(audit)).then();
    }
}
