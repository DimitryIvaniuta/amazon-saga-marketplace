package com.github.dimitryivaniuta.marketplace.common.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.github.dimitryivaniuta.marketplace.common.event.EventEnvelope;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

/**
 * Persists event envelopes in the caller's local database transaction.
 */
@RequiredArgsConstructor
public class EventStore {

    /** Reactive SQL client. */
    private final DatabaseClient databaseClient;
    /** JSON serializer. */
    private final JsonMapper objectMapper;

    /**
     * Adds an envelope to the local outbox.
     *
     * @param aggregateId aggregate identifier used as Kafka key
     * @param topic destination topic
     * @param event event envelope
     * @return completion signal
     */
    public Mono<Void> append(String aggregateId, String topic, EventEnvelope event) {
        final String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            return Mono.error(new IllegalArgumentException("Cannot serialize event", exception));
        }
        return databaseClient.sql("""
                INSERT INTO outbox_event(
                    id, aggregate_id, topic, event_type, payload, status, attempts, created_at)
                VALUES (:id, :aggregateId, :topic, :eventType, CAST(:payload AS jsonb), 'NEW', 0, now())
                """)
                .bind("id", event.eventId())
                .bind("aggregateId", aggregateId)
                .bind("topic", topic)
                .bind("eventType", event.eventType())
                .bind("payload", json)
                .fetch()
                .rowsUpdated()
                .then();
    }

    /**
     * Creates and appends a first-version event.
     *
     * @param aggregateId aggregate identifier
     * @param topic Kafka topic
     * @param eventType event contract name
     * @param correlationId workflow correlation identifier
     * @param causationId causing event identifier
     * @param payload event payload
     * @return the stored envelope
     */
    public Mono<EventEnvelope> createAndAppend(
            String aggregateId,
            String topic,
            String eventType,
            String correlationId,
            String causationId,
            JsonNode payload) {
        EventEnvelope event = EventEnvelope.create(eventType, correlationId, causationId, payload);
        return append(aggregateId, topic, event).thenReturn(event);
    }
}
