package com.github.dimitryivaniuta.marketplace.provider.repository;

import com.github.dimitryivaniuta.marketplace.common.web.ApiException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** Provider-side payment and idempotency persistence. */
@Repository
@RequiredArgsConstructor
public class ProviderRepository {
    /** Reactive SQL client. */
    private final DatabaseClient databaseClient;

    /** @param key operation key @return cached operation */
    public Mono<OperationRow> operation(String key) {
        return databaseClient.sql("""
                SELECT operation_key, operation, payment_id, status, reference
                  FROM provider_operation WHERE operation_key = :key
                """).bind("key", key).map((row, metadata) -> new OperationRow(
                        row.get("operation_key", String.class), row.get("operation", String.class),
                        row.get("payment_id", UUID.class), row.get("status", String.class),
                        row.get("reference", String.class))).one();
    }

    /** Creates authorization and its idempotency result atomically. */
    public Mono<OperationRow> authorize(
            String key, UUID orderId, long amount, String currency, String token) {
        UUID paymentId = UUID.randomUUID();
        String reference = "pay_" + paymentId.toString().replace("-", "").substring(0, 20);
        return databaseClient.sql("""
                INSERT INTO provider_payment(
                    id, merchant_order_id, authorization_key, amount_minor, currency,
                    payment_token_fingerprint, status, reference, created_at, updated_at)
                VALUES (:id, :orderId, :key, :amount, :currency, :fingerprint,
                        'AUTHORIZED', :reference, now(), now())
                ON CONFLICT (authorization_key) DO NOTHING
                """).bind("id", paymentId).bind("orderId", orderId).bind("key", key)
                .bind("amount", amount).bind("currency", currency)
                .bind("fingerprint", Integer.toHexString(token.hashCode())).bind("reference", reference)
                .fetch().rowsUpdated()
                .then(databaseClient.sql("""
                        INSERT INTO provider_operation(operation_key, operation, payment_id, status, reference, created_at)
                        SELECT :key, 'AUTHORIZE', id, status, reference, now()
                          FROM provider_payment WHERE authorization_key = :key
                        ON CONFLICT (operation_key) DO NOTHING
                        """).bind("key", key).fetch().rowsUpdated())
                .then(operation(key));
    }

    /** Applies a provider state transition and caches the result under the operation key. */
    public Mono<OperationRow> transition(
            String key, UUID paymentId, String operation, String expected, String target) {
        return databaseClient.sql("""
                UPDATE provider_payment SET status = :target, updated_at = now()
                 WHERE id = :id AND status = :expected
                """).bind("target", target).bind("id", paymentId).bind("expected", expected)
                .fetch().rowsUpdated()
                .flatMap(changed -> changed == 1L
                        ? insertOperation(key, paymentId, operation)
                        : byId(paymentId).flatMap(payment -> target.equals(payment.status())
                                ? insertOperation(key, paymentId, operation)
                                : Mono.error(new ApiException(HttpStatus.CONFLICT,
                                        "INVALID_PROVIDER_TRANSITION",
                                        "Provider payment cannot transition from "
                                                + payment.status() + " to " + target))))
                .then(operation(key));
    }

    private Mono<Long> insertOperation(String key, UUID paymentId, String operation) {
        return databaseClient.sql("""
                INSERT INTO provider_operation(operation_key, operation, payment_id, status, reference, created_at)
                SELECT :key, :operation, id, status, reference, now()
                  FROM provider_payment WHERE id = :id
                ON CONFLICT (operation_key) DO NOTHING
                """).bind("key", key).bind("operation", operation).bind("id", paymentId)
                .fetch().rowsUpdated();
    }

    /** @param authorizationKey key @return provider payment */
    public Mono<PaymentRow> byAuthorizationKey(String authorizationKey) {
        return databaseClient.sql("""
                SELECT id, status, reference, payment_token_fingerprint
                  FROM provider_payment WHERE authorization_key = :key
                """).bind("key", authorizationKey).map((row, metadata) -> new PaymentRow(
                        row.get("id", UUID.class), row.get("status", String.class),
                        row.get("reference", String.class), row.get("payment_token_fingerprint", String.class))).one();
    }

    /** @param paymentId id @return provider payment */
    public Mono<PaymentRow> byId(UUID paymentId) {
        return databaseClient.sql("""
                SELECT id, status, reference, payment_token_fingerprint
                  FROM provider_payment WHERE id = :id
                """).bind("id", paymentId).map((row, metadata) -> new PaymentRow(
                        row.get("id", UUID.class), row.get("status", String.class),
                        row.get("reference", String.class), row.get("payment_token_fingerprint", String.class))).one();
    }

    /** @param operationKey key @param operation operation @param paymentId payment @param status result @param reference ref */
    public record OperationRow(
            String operationKey, String operation, UUID paymentId, String status, String reference) { }
    /** @param id payment @param status state @param reference reference @param tokenFingerprint non-sensitive token fingerprint */
    public record PaymentRow(UUID id, String status, String reference, String tokenFingerprint) { }
}
