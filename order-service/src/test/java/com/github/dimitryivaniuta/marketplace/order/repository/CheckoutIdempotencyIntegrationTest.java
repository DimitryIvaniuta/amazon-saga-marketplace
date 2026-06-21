package com.github.dimitryivaniuta.marketplace.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.r2dbc.spi.ConnectionFactories;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** PostgreSQL integration tests for checkout idempotency and transaction boundaries. */
@Testcontainers
class CheckoutIdempotencyIntegrationTest {

    /** Disposable PostgreSQL instance matching production. */
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.4")
                    .withDatabaseName("order_test")
                    .withUsername("marketplace")
                    .withPassword("marketplace");

    /** Repository under test. */
    private static OrderRepository repository;
    /** Reactive transaction operator. */
    private static TransactionalOperator transactionalOperator;

    /** Applies real Flyway migrations and creates the R2DBC repository. */
    @BeforeAll
    static void initializeDatabase() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        String r2dbcUrl = "r2dbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/" + POSTGRES.getDatabaseName()
                + "?user=" + POSTGRES.getUsername() + "&password=" + POSTGRES.getPassword();
        var connectionFactory = ConnectionFactories.get(r2dbcUrl);
        repository = new OrderRepository(DatabaseClient.create(connectionFactory));
        transactionalOperator = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
    }

    /** Clears business tables between tests. */
    @BeforeEach
    void resetDatabase() throws Exception {
        try (Connection connection = jdbcConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     TRUNCATE TABLE order_line, order_saga, customer_order,
                         checkout_idempotency, outbox_event, inbox_event CASCADE
                     """)) {
            statement.execute();
        }
    }

    /** Concurrent retries resolve to one durable order mapping and one owner. */
    @Test
    void shouldSelectOneOwnerForConcurrentCheckoutClaims() {
        UUID userId = UUID.randomUUID();
        UUID firstOrder = UUID.randomUUID();
        UUID secondOrder = UUID.randomUUID();
        String requestHash = OrderRepository.hash("same-checkout-request");

        var claims = Mono.zip(
                        repository.claim(userId, "checkout-key", requestHash, firstOrder),
                        repository.claim(userId, "checkout-key", requestHash, secondOrder))
                .block();

        assertThat(claims).isNotNull();
        assertThat(claims.getT1().orderId()).isEqualTo(claims.getT2().orderId());
        assertThat(claims.getT1().owner()).isNotEqualTo(claims.getT2().owner());
        assertThat(claims.getT1().requestHash()).isEqualTo(requestHash);
        assertThat(claims.getT2().requestHash()).isEqualTo(requestHash);
    }

    /** Reusing a key with different content exposes the original hash for conflict handling. */
    @Test
    void shouldExposeOriginalHashWhenKeyIsReusedForDifferentRequest() {
        UUID userId = UUID.randomUUID();
        var original = repository.claim(
                userId, "reused-key", OrderRepository.hash("request-a"), UUID.randomUUID()).block();
        var retry = repository.claim(
                userId, "reused-key", OrderRepository.hash("request-b"), UUID.randomUUID()).block();

        assertThat(original).isNotNull();
        assertThat(retry).isNotNull();
        assertThat(retry.owner()).isFalse();
        assertThat(retry.orderId()).isEqualTo(original.orderId());
        assertThat(retry.requestHash()).isEqualTo(original.requestHash());
    }

    /** A failed checkout transaction cannot permanently consume its idempotency key. */
    @Test
    void shouldRollbackClaimWhenOrderCreationTransactionFails() {
        UUID userId = UUID.randomUUID();
        String key = "rollback-key";

        Mono<Void> failedCheckout = repository.claim(
                        userId, key, OrderRepository.hash("request"), UUID.randomUUID())
                .then(Mono.<Void>error(new IllegalStateException("simulated order write failure")))
                .as(transactionalOperator::transactional);

        StepVerifier.create(failedCheckout)
                .expectErrorMessage("simulated order write failure")
                .verify();
        StepVerifier.create(repository.findClaim(userId, key))
                .verifyComplete();
    }

    private static Connection jdbcConnection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
