package com.github.dimitryivaniuta.marketplace.common.event;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, versioned envelope used for all asynchronous marketplace messages.
 * The envelope gives every delivery a stable identity so consumers can provide
 * effectively-once business processing on top of Kafka's at-least-once delivery.
 *
 * @param eventId unique event identifier
 * @param eventType stable event contract name
 * @param correlationId identifier shared by the whole checkout workflow
 * @param causationId identifier of the message that caused this event
 * @param occurredAt event creation time in UTC
 * @param schemaVersion payload schema version
 * @param payload event-specific JSON payload
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        String correlationId,
        String causationId,
        Instant occurredAt,
        int schemaVersion,
        JsonNode payload) {

    /**
     * Validates required envelope invariants.
     */
    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(payload, "payload");
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
    }

    /**
     * Creates a new first-version event with a generated identifier and current timestamp.
     *
     * @param eventType stable event contract name
     * @param correlationId checkout correlation identifier
     * @param causationId optional causing message identifier
     * @param payload event payload
     * @return a new event envelope
     */
    public static EventEnvelope create(
            String eventType,
            String correlationId,
            String causationId,
            JsonNode payload) {
        return new EventEnvelope(
                UUID.randomUUID(), eventType, correlationId, causationId, Instant.now(), 1, payload);
    }
}
