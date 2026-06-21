package com.github.dimitryivaniuta.marketplace.inventory.messaging;

import tools.jackson.databind.json.JsonMapper;
import com.github.dimitryivaniuta.marketplace.common.event.EventEnvelope;
import com.github.dimitryivaniuta.marketplace.common.event.Topics;
import com.github.dimitryivaniuta.marketplace.inventory.service.InventoryService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka adapter for inventory commands. */
@Component
@RequiredArgsConstructor
public class InventoryCommandListener {

    /** JSON mapper. */
    private final JsonMapper objectMapper;
    /** Inventory use cases. */
    private final InventoryService inventoryService;

    /**
     * Processes a message synchronously on the Kafka consumer thread so the offset
     * is committed only after the reactive database transaction completes.
     *
     * @param json serialized envelope
     * @throws Exception for retryable processing failures
     */
    @KafkaListener(topics = Topics.INVENTORY_COMMANDS, groupId = "inventory-service-v1")
    public void onCommand(String json) throws Exception {
        EventEnvelope event = objectMapper.readValue(json, EventEnvelope.class);
        inventoryService.handle(event).block(Duration.ofSeconds(30));
    }
}
