package com.github.dimitryivaniuta.marketplace.order.messaging;

import tools.jackson.databind.json.JsonMapper;
import com.github.dimitryivaniuta.marketplace.common.event.EventEnvelope;
import com.github.dimitryivaniuta.marketplace.common.event.Topics;
import com.github.dimitryivaniuta.marketplace.order.service.OrderSagaService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Receives participant events for the order Saga. */
@Component
@RequiredArgsConstructor
public class OrderEventListener {
    /** JSON mapper. */
    private final JsonMapper objectMapper;
    /** Saga orchestrator. */
    private final OrderSagaService sagaService;

    /** @param json event JSON @throws Exception processing error */
    @KafkaListener(topics = {Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS, Topics.SHIPPING_EVENTS},
            groupId = "order-service-v1")
    public void onEvent(String json) throws Exception {
        sagaService.handle(objectMapper.readValue(json, EventEnvelope.class)).block(Duration.ofSeconds(30));
    }
}
