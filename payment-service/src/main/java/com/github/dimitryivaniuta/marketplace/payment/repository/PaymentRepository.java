package com.github.dimitryivaniuta.marketplace.payment.repository;

import com.github.dimitryivaniuta.marketplace.payment.domain.PaymentOperation;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Durable payment, attempt, and reconciliation persistence gateway. */
@Repository
@RequiredArgsConstructor
public class PaymentRepository {

    /** Reactive SQL client. */
    private final DatabaseClient databaseClient;

    /** @param orderId order @param amount amount @param currency currency @param token opaque token @return payment */
    public Mono<PaymentRow> createOrLoad(UUID orderId, long amount, String currency, String token) {
        UUID paymentId = UUID.randomUUID();
        return databaseClient.sql("""
                INSERT INTO payment(id, order_id, status, amount_minor, currency, payment_token,
                                    version, created_at, updated_at)
                VALUES (:id, :orderId, 'NEW', :amount, :currency, :token, 0, now(), now())
                ON CONFLICT (order_id) DO NOTHING
                """)
                .bind("id", paymentId).bind("orderId", orderId).bind("amount", amount)
                .bind("currency", currency).bind("token", token).fetch().rowsUpdated()
                .then(findByOrderId(orderId));
    }

    /** @param orderId order @return payment */
    public Mono<PaymentRow> findByOrderId(UUID orderId) {
        return databaseClient.sql("""
                SELECT id, order_id, status, amount_minor, currency, payment_token,
                       provider_payment_id, provider_reference, authorization_key,
                       version, created_at, updated_at
                  FROM payment WHERE order_id = :orderId
                """).bind("orderId", orderId).map((row, metadata) -> map(row)).one();
    }

    /** @param status unknown status @return payments requiring reconciliation */
    public Flux<PaymentRow> findForReconciliation(String status) {
        return databaseClient.sql("""
                SELECT id, order_id, status, amount_minor, currency, payment_token,
                       provider_payment_id, provider_reference, authorization_key,
                       version, created_at, updated_at
                  FROM payment WHERE status = :status AND updated_at < now() - interval '2 seconds'
                 ORDER BY updated_at LIMIT 100
                """).bind("status", status).map((row, metadata) -> map(row)).all();
    }

    /** @param paymentId payment @param operation operation @param key idempotency key @return attempt id */
    public Mono<UUID> startAttempt(UUID paymentId, PaymentOperation operation, String key) {
        UUID attemptId = UUID.randomUUID();
        return databaseClient.sql("""
                INSERT INTO payment_attempt(id, payment_id, operation, idempotency_key, status, started_at)
                VALUES (:id, :paymentId, :operation, :key, 'STARTED', now())
                ON CONFLICT (payment_id, operation, idempotency_key) DO UPDATE SET started_at = payment_attempt.started_at
                RETURNING id
                """).bind("id", attemptId).bind("paymentId", paymentId)
                .bind("operation", operation.name()).bind("key", key)
                .map((row, metadata) -> row.get("id", UUID.class)).one();
    }

    /** @param paymentId payment @param expected expected state @param next next state @return updated rows */
    public Mono<Long> transition(UUID paymentId, String expected, String next) {
        return databaseClient.sql("""
                UPDATE payment SET status = :next, version = version + 1, updated_at = now()
                 WHERE id = :id AND status = :expected
                """).bind("next", next).bind("id", paymentId).bind("expected", expected)
                .fetch().rowsUpdated();
    }

    /** Persists provider authorization output. */
    public Mono<Void> authorizationSucceeded(
            UUID paymentId, UUID attemptId, UUID providerId, String reference, String key) {
        return databaseClient.sql("""
                UPDATE payment SET status = 'AUTHORIZED', provider_payment_id = :providerId,
                       provider_reference = :reference, authorization_key = :key, payment_token = NULL,
                       version = version + 1, updated_at = now()
                 WHERE id = :paymentId
                """).bind("providerId", providerId).bind("reference", reference).bind("key", key)
                .bind("paymentId", paymentId).fetch().rowsUpdated()
                .then(completeAttempt(attemptId, "SUCCEEDED", reference, null));
    }

    /** Persists an authorization result discovered by reconciliation. */
    public Mono<Void> markAuthorizationReconciled(
            UUID paymentId, UUID providerId, String reference, String key) {
        return databaseClient.sql("""
                UPDATE payment SET status = 'AUTHORIZED', provider_payment_id = :providerId,
                       provider_reference = :reference, authorization_key = :key, payment_token = NULL,
                       last_error = NULL, version = version + 1, updated_at = now()
                 WHERE id = :paymentId AND status = 'AUTHORIZATION_UNKNOWN'
                """).bind("providerId", providerId).bind("reference", reference).bind("key", key)
                .bind("paymentId", paymentId).fetch().rowsUpdated().then();
    }

    /** Persists successful capture/cancel/refund output. */
    public Mono<Void> operationSucceeded(UUID paymentId, UUID attemptId, String status, String reference) {
        return databaseClient.sql("""
                UPDATE payment SET status = :status, provider_reference = :reference,
                       version = version + 1, updated_at = now() WHERE id = :paymentId
                """).bind("status", status).bind("reference", reference).bind("paymentId", paymentId)
                .fetch().rowsUpdated().then(completeAttempt(attemptId, "SUCCEEDED", reference, null));
    }

    /** Persists a definitive operation failure. */
    public Mono<Void> operationFailed(UUID paymentId, UUID attemptId, String status, String error) {
        return databaseClient.sql("""
                UPDATE payment SET status = :status, last_error = :error,
                       payment_token = CASE WHEN :status = 'AUTHORIZATION_FAILED' THEN NULL ELSE payment_token END,
                       version = version + 1, updated_at = now() WHERE id = :paymentId
                """).bind("status", status).bind("error", bounded(error)).bind("paymentId", paymentId)
                .fetch().rowsUpdated().then(completeAttempt(attemptId, "FAILED", null, error));
    }

    /** Persists an unknown network outcome without emitting a business failure. */
    public Mono<Void> operationUnknown(UUID paymentId, UUID attemptId, String status, String error) {
        return databaseClient.sql("""
                UPDATE payment SET status = :status, last_error = :error,
                       payment_token = CASE WHEN :status = 'AUTHORIZATION_FAILED' THEN NULL ELSE payment_token END,
                       version = version + 1, updated_at = now() WHERE id = :paymentId
                """).bind("status", status).bind("error", bounded(error)).bind("paymentId", paymentId)
                .fetch().rowsUpdated().then(completeAttempt(attemptId, "UNKNOWN", null, error));
    }

    /**
     * Completes a durable provider attempt. This method may be invoked through
     * the dedicated requires-new transaction operator before the parent payment
     * state/outbox transaction begins.
     *
     * @param attemptId attempt identifier
     * @param status result status
     * @param reference provider reference, when known
     * @param error diagnostic, when present
     * @return completion signal
     */
    public Mono<Void> completeAttempt(
            UUID attemptId, String status, String reference, String error) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                UPDATE payment_attempt SET status = :status, provider_reference = :reference,
                       error_message = :error, finished_at = now() WHERE id = :id
                """).bind("status", status).bind("id", attemptId);
        spec = reference == null ? spec.bindNull("reference", String.class) : spec.bind("reference", reference);
        spec = error == null ? spec.bindNull("error", String.class) : spec.bind("error", bounded(error));
        return spec.fetch().rowsUpdated().then();
    }

    private PaymentRow map(io.r2dbc.spi.Row row) {
        OffsetDateTime created = row.get("created_at", OffsetDateTime.class);
        OffsetDateTime updated = row.get("updated_at", OffsetDateTime.class);
        return new PaymentRow(row.get("id", UUID.class), row.get("order_id", UUID.class),
                row.get("status", String.class), row.get("amount_minor", Long.class),
                row.get("currency", String.class), row.get("payment_token", String.class),
                row.get("provider_payment_id", UUID.class), row.get("provider_reference", String.class),
                row.get("authorization_key", String.class), row.get("version", Long.class),
                created == null ? null : created.toInstant(), updated == null ? null : updated.toInstant());
    }

    private String bounded(String value) {
        if (value == null) {
            return "Unknown provider failure";
        }
        return value.length() <= 1900 ? value : value.substring(0, 1900);
    }

    /** Durable payment projection. */
    public record PaymentRow(
            UUID id, UUID orderId, String status, long amountMinor, String currency,
            String paymentToken, UUID providerPaymentId, String providerReference,
            String authorizationKey, long version, java.time.Instant createdAt,
            java.time.Instant updatedAt) { }
}
