package com.github.dimitryivaniuta.marketplace.common.messaging;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Schedules transactional-outbox publication without overlapping polls and
 * keeps the scheduling thread attached to the active batch during shutdown.
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxSchedulingConfiguration {

    /** Maximum time allowed for one publication batch. */
    private static final Duration BATCH_TIMEOUT = Duration.ofSeconds(30);
    /** Outbox publisher. */
    private final OutboxPublisher publisher;

    /** Polls pending outbox rows at a configurable fixed delay. */
    @Scheduled(fixedDelayString = "${marketplace.outbox.poll-delay:250ms}")
    public void publish() {
        try {
            publisher.publishBatch().block(BATCH_TIMEOUT);
        } catch (RuntimeException exception) {
            log.error("Unexpected outbox poll failure", exception);
        }
    }
}
