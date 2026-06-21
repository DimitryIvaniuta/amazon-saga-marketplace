package com.github.dimitryivaniuta.marketplace.common.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Claimed transactional-outbox row ready to be sent to Kafka.
 *
 * @param id outbox row identifier
 * @param aggregateId Kafka message key and aggregate identifier
 * @param topic destination topic
 * @param payload serialized event envelope
 * @param createdAt creation timestamp
 * @param attempts previous publication attempts
 */
public record OutboxMessage(
        UUID id,
        String aggregateId,
        String topic,
        String payload,
        Instant createdAt,
        int attempts) {
}
