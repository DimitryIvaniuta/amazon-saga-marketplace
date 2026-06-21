package com.github.dimitryivaniuta.marketplace.common.messaging;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for the common transactional-outbox table contract.
 * Claiming is a single PostgreSQL statement using {@code FOR UPDATE SKIP LOCKED}
 * so multiple service replicas can safely publish in parallel.
 */
@RequiredArgsConstructor
public class OutboxRepository {

    /** Reactive SQL client. */
    private final DatabaseClient databaseClient;

    /**
     * Atomically claims a bounded batch, including abandoned claims whose lease expired.
     *
     * @param batchSize maximum number of rows
     * @param claimTimeout processing lease duration
     * @return claimed messages
     */
    public Flux<OutboxMessage> claim(int batchSize, Duration claimTimeout) {
        return databaseClient.sql("""
                WITH candidates AS (
                    SELECT id
                    FROM outbox_event
                    WHERE status = 'NEW'
                       OR (status = 'PROCESSING' AND claimed_at < now() - (:timeoutSeconds * interval '1 second'))
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT :batchSize
                )
                UPDATE outbox_event o
                   SET status = 'PROCESSING', claimed_at = now()
                  FROM candidates c
                 WHERE o.id = c.id
                RETURNING o.id, o.aggregate_id, o.topic, o.payload::text, o.created_at, o.attempts
                """)
                .bind("timeoutSeconds", claimTimeout.toSeconds())
                .bind("batchSize", batchSize)
                .map((row, metadata) -> new OutboxMessage(
                        row.get("id", UUID.class),
                        row.get("aggregate_id", String.class),
                        row.get("topic", String.class),
                        row.get("payload", String.class),
                        row.get("created_at", java.time.OffsetDateTime.class).toInstant(),
                        row.get("attempts", Integer.class)))
                .all();
    }

    /**
     * Marks a message as durably published.
     *
     * @param id outbox identifier
     * @return completion signal
     */
    public Mono<Void> markPublished(UUID id) {
        return databaseClient.sql("""
                UPDATE outbox_event
                   SET status = 'PUBLISHED', published_at = now(), last_error = NULL
                 WHERE id = :id
                """)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .then();
    }

    /**
     * Releases a failed claim for a later retry while preserving diagnostic details.
     *
     * @param id outbox identifier
     * @param error bounded error description
     * @return completion signal
     */
    public Mono<Void> markFailed(UUID id, String error) {
        String safeError = error == null ? "Unknown publication error" : error.substring(0, Math.min(error.length(), 2000));
        return databaseClient.sql("""
                UPDATE outbox_event
                   SET status = 'NEW', attempts = attempts + 1, last_error = :error
                 WHERE id = :id
                """)
                .bind("error", safeError)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .then();
    }
}
