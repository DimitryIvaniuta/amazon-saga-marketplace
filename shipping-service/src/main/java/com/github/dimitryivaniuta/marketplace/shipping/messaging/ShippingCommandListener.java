package com.github.dimitryivaniuta.marketplace.shipping.messaging;

import tools.jackson.databind.json.JsonMapper;
import com.github.dimitryivaniuta.marketplace.common.event.EventEnvelope;
import com.github.dimitryivaniuta.marketplace.common.event.Topics;
import com.github.dimitryivaniuta.marketplace.shipping.service.ShippingService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka adapter for shipping commands. */
@Component
@RequiredArgsConstructor
public class ShippingCommandListener {
    /** JSON mapper. */
    private final JsonMapper objectMapper;
    /** Shipping service. */
    private final ShippingService shippingService;

    /** @param json serialized command @throws Exception for retryable failures */
    @KafkaListener(topics = Topics.SHIPPING_COMMANDS, groupId = "shipping-service-v1")
    public void onCommand(String json) throws Exception {
        shippingService.handle(objectMapper.readValue(json, EventEnvelope.class))
                .block(Duration.ofSeconds(30));
    }
}
