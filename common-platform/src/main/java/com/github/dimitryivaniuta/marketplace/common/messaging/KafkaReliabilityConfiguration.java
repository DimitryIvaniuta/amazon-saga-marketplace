package com.github.dimitryivaniuta.marketplace.common.messaging;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/** Shared bounded-retry and dead-letter policy for Kafka consumers. */
@AutoConfiguration
@ConditionalOnClass(CommonErrorHandler.class)
@ConditionalOnBean(KafkaTemplate.class)
public class KafkaReliabilityConfiguration {

    /**
     * Retries transient listener failures with exponential backoff and sends a
     * terminal delivery to the matching partition of {@code <topic>.DLT}.
     *
     * @param kafkaTemplate producer used by the recoverer
     * @return common listener error handler
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public CommonErrorHandler marketplaceKafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + ".DLT", record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxInterval(5_000L);
        backOff.setMaxElapsedTime(30_000L);
        ExponentialBackOff contentionBackOff = new ExponentialBackOff(20L, 1.5);
        contentionBackOff.setMaxInterval(100L);
        contentionBackOff.setMaxElapsedTime(1_000L);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.setBackOffFunction((record, exception) ->
                isTransientContention(exception) ? contentionBackOff : null);
        handler.setAckAfterHandle(true);
        return handler;
    }

    /** @param failure listener failure @return whether the cause chain is short-lived contention */
    static boolean isTransientContention(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TransientContentionException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
