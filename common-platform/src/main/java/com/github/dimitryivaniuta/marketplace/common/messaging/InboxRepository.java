package com.github.dimitryivaniuta.marketplace.common.messaging;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

/**
 * Transactional-inbox repository used to suppress duplicate Kafka deliveries.
 * The insert must execute inside the same local transaction as the business update.
 */
@RequiredArgsConstructor
public class InboxRepository {

    /** Reactive SQL client. */
    private final DatabaseClient databaseClient;

    /**
     * Attempts to claim an event for a named consumer.
     *
     * @param eventId event identifier
     * @param consumer logical consumer name
     * @return {@code true} only for the first successful insert
     */
    public Mono<Boolean> tryStart(UUID eventId, String consumer) {
        return databaseClient.sql("""
                INSERT INTO inbox_event(event_id, consumer_name, processed_at)
                VALUES (:eventId, :consumer, now())
                ON CONFLICT (event_id, consumer_name) DO NOTHING
                """)
                .bind("eventId", eventId)
                .bind("consumer", consumer)
                .fetch()
                .rowsUpdated()
                .map(count -> count == 1L);
    }
}
