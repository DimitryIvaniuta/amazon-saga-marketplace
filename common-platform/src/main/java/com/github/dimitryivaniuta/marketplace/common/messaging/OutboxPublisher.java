package com.github.dimitryivaniuta.marketplace.common.messaging;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Publishes rows claimed from a transactional outbox.
 * A crash after Kafka accepts a record but before the row is marked published may
 * cause a duplicate; every consumer therefore also uses a transactional inbox.
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {

    /** Publication batch size. */
    private static final int BATCH_SIZE = 100;
    /** Lease applied to claimed rows. */
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(2);

    /** Kafka producer. */
    private final KafkaTemplate<String, String> kafkaTemplate;
    /** Outbox persistence gateway. */
    private final OutboxRepository repository;
    /** Prevents overlapping local polls. */
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * Publishes one claimed batch. Concurrent invocations on the same instance are ignored.
     *
     * @return completion signal
     */
    public Mono<Void> publishBatch() {
        if (!running.compareAndSet(false, true)) {
            return Mono.empty();
        }
        return repository.claim(BATCH_SIZE, CLAIM_TIMEOUT)
                .concatMap(this::publishOne)
                .then()
                .doFinally(signal -> running.set(false));
    }

    /**
     * Waits for an in-progress poll during graceful application shutdown.
     *
     * @return true when no poll is active
     */
    public boolean isIdle() {
        return !running.get();
    }

    private Mono<Void> publishOne(OutboxMessage message) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(message.topic(), message.aggregateId(), message.payload());
        record.headers().add("event-id", message.id().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return Mono.fromFuture(kafkaTemplate.send(record))
                .then(repository.markPublished(message.id()))
                .doOnSuccess(ignored -> log.debug("Published outbox event {} to {}", message.id(), message.topic()))
                .onErrorResume(error -> repository.markFailed(message.id(), error.getMessage())
                        .then(Mono.fromRunnable(() -> log.warn(
                                "Failed to publish outbox event {} on attempt {}",
                                message.id(), message.attempts() + 1, error))));
    }
}
