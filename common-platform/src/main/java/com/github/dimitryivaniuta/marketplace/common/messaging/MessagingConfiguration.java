package com.github.dimitryivaniuta.marketplace.common.messaging;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Reusable transactional outbox/inbox infrastructure configuration.
 */
@AutoConfiguration
@EnableScheduling
@ConditionalOnClass({DatabaseClient.class, KafkaTemplate.class})
@ConditionalOnProperty(name = "marketplace.outbox.enabled", havingValue = "true")
public class MessagingConfiguration {

    /**
     * Creates the shared outbox repository.
     *
     * @param databaseClient reactive SQL client
     * @return outbox repository
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxRepository outboxRepository(DatabaseClient databaseClient) {
        return new OutboxRepository(databaseClient);
    }

    /**
     * Creates the shared transactional inbox repository.
     *
     * @param databaseClient reactive SQL client
     * @return inbox repository
     */
    @Bean
    @ConditionalOnMissingBean
    public InboxRepository inboxRepository(DatabaseClient databaseClient) {
        return new InboxRepository(databaseClient);
    }

    /**
     * Creates the local event store.
     *
     * @param databaseClient reactive SQL client
     * @param objectMapper JSON mapper
     * @return event store
     */
    @Bean
    @ConditionalOnMissingBean
    public EventStore eventStore(DatabaseClient databaseClient, JsonMapper objectMapper) {
        return new EventStore(databaseClient, objectMapper);
    }

    /**
     * Creates the outbox publisher.
     *
     * @param kafkaTemplate Kafka producer
     * @param repository outbox repository
     * @return outbox publisher
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxPublisher outboxPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxRepository repository) {
        return new OutboxPublisher(kafkaTemplate, repository);
    }

    /**
     * Creates the fixed-delay publisher adapter. Keeping it as an auto-configured
     * bean avoids broad component scanning in services that do not use Kafka.
     *
     * @param publisher outbox publisher
     * @return scheduling adapter
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxSchedulingConfiguration outboxSchedulingConfiguration(
            OutboxPublisher publisher) {
        return new OutboxSchedulingConfiguration(publisher);
    }
}
