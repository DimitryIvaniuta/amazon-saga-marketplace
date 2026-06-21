package com.github.dimitryivaniuta.marketplace.provider.service;

import com.github.dimitryivaniuta.marketplace.common.web.ApiException;
import com.github.dimitryivaniuta.marketplace.provider.api.ProviderContracts;
import com.github.dimitryivaniuta.marketplace.provider.repository.ProviderRepository;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/** Deterministic provider behavior with idempotent operation storage. */
@Service
@RequiredArgsConstructor
public class ProviderService {
    /** Provider repository. */
    private final ProviderRepository repository;
    /** Local transaction operator. */
    private final TransactionalOperator transactionalOperator;

    /** @param key idempotency key @param request request @return authorization result */
    public Mono<ProviderContracts.ProviderResponse> authorize(
            String key, ProviderContracts.AuthorizeRequest request) {
        return repository.operation(key).switchIfEmpty(Mono.defer(() -> {
            if ("tok_declined".equals(request.paymentToken())) {
                return Mono.error(new ApiException(HttpStatus.PAYMENT_REQUIRED,
                        "PAYMENT_DECLINED", "The test provider declined this token"));
            }
            return repository.authorize(key, request.orderId(), request.amountMinor(),
                            request.currency().toUpperCase(Locale.ROOT), request.paymentToken())
                    .as(transactionalOperator::transactional);
        })).map(this::response)
                .delayElement("tok_slow_authorize".equals(request.paymentToken())
                        ? Duration.ofSeconds(8) : Duration.ZERO);
    }

    /** @param key operation key @param paymentId provider payment @return capture result */
    public Mono<ProviderContracts.ProviderResponse> capture(String key, UUID paymentId) {
        return repository.operation(key).switchIfEmpty(repository.byId(paymentId).flatMap(payment -> {
            if (payment.tokenFingerprint().equals(Integer.toHexString("tok_capture_fail".hashCode()))) {
                return Mono.error(new ApiException(HttpStatus.PAYMENT_REQUIRED,
                        "CAPTURE_DECLINED", "The test provider declined capture"));
            }
            return repository.transition(key, paymentId, "CAPTURE", "AUTHORIZED", "CAPTURED")
                    .as(transactionalOperator::transactional);
        })).map(this::response);
    }

    /** @param key operation key @param paymentId provider payment @return cancellation result */
    public Mono<ProviderContracts.ProviderResponse> cancel(String key, UUID paymentId) {
        return repository.operation(key)
                .switchIfEmpty(repository.transition(key, paymentId, "CANCEL", "AUTHORIZED", "CANCELLED")
                        .as(transactionalOperator::transactional))
                .map(this::response);
    }

    /** @param key operation key @param paymentId provider payment @return refund result */
    public Mono<ProviderContracts.ProviderResponse> refund(String key, UUID paymentId) {
        return repository.operation(key)
                .switchIfEmpty(repository.transition(key, paymentId, "REFUND", "CAPTURED", "REFUNDED")
                        .as(transactionalOperator::transactional))
                .map(this::response);
    }

    /** @param key authorization key @return payment projection */
    public Mono<ProviderContracts.ProviderResponse> find(String key) {
        return repository.byAuthorizationKey(key)
                .map(row -> new ProviderContracts.ProviderResponse(row.id(), row.status(), row.reference()));
    }

    /** @param paymentId provider payment id @return payment projection */
    public Mono<ProviderContracts.ProviderResponse> findById(UUID paymentId) {
        return repository.byId(paymentId)
                .map(row -> new ProviderContracts.ProviderResponse(row.id(), row.status(), row.reference()));
    }

    private ProviderContracts.ProviderResponse response(ProviderRepository.OperationRow row) {
        return new ProviderContracts.ProviderResponse(row.paymentId(), row.status(), row.reference());
    }
}
