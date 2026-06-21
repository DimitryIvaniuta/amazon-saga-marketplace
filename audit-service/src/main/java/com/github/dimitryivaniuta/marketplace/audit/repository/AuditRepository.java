package com.github.dimitryivaniuta.marketplace.audit.repository;

import com.github.dimitryivaniuta.marketplace.common.event.EventEnvelope;
import com.github.dimitryivaniuta.marketplace.common.event.WorkflowPayloads;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Append-only audit persistence independent from business-service transactions. */
@Repository
@RequiredArgsConstructor
public class AuditRepository {
    /** Reactive SQL client. */
    private final DatabaseClient databaseClient;

    /** @param event envelope @param audit audit payload @return completion */
    public Mono<Void> append(EventEnvelope event, WorkflowPayloads.Audit audit) {
        return databaseClient.sql("""
                INSERT INTO audit_entry(id, event_id, source, action, aggregate_type,
                                        aggregate_id, outcome, details, occurred_at, received_at)
                VALUES (:id, :eventId, :source, :action, :aggregateType,
                        :aggregateId, :outcome, :details, :occurredAt, now())
                ON CONFLICT (event_id) DO NOTHING
                """).bind("id", UUID.randomUUID()).bind("eventId", event.eventId())
                .bind("source", audit.source()).bind("action", audit.action())
                .bind("aggregateType", audit.aggregateType()).bind("aggregateId", audit.aggregateId())
                .bind("outcome", audit.outcome()).bind("details", bounded(audit.details()))
                .bind("occurredAt", audit.occurredAt()).fetch().rowsUpdated().then();
    }

    /** @param aggregateId aggregate id @return entries ordered by occurrence */
    public Flux<AuditRow> find(String aggregateId) {
        return databaseClient.sql("""
                SELECT id, event_id, source, action, aggregate_type, aggregate_id,
                       outcome, details, occurred_at, received_at
                  FROM audit_entry WHERE aggregate_id = :aggregateId ORDER BY occurred_at, id
                """).bind("aggregateId", aggregateId).map((row, metadata) -> new AuditRow(
                        row.get("id", UUID.class), row.get("event_id", UUID.class),
                        row.get("source", String.class), row.get("action", String.class),
                        row.get("aggregate_type", String.class), row.get("aggregate_id", String.class),
                        row.get("outcome", String.class), row.get("details", String.class),
                        row.get("occurred_at", OffsetDateTime.class).toInstant(),
                        row.get("received_at", OffsetDateTime.class).toInstant())).all();
    }

    private String bounded(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 4000 ? value : value.substring(0, 4000);
    }

    /** Immutable audit projection. */
    public record AuditRow(
            UUID id, UUID eventId, String source, String action, String aggregateType,
            String aggregateId, String outcome, String details,
            java.time.Instant occurredAt, java.time.Instant receivedAt) { }
}
