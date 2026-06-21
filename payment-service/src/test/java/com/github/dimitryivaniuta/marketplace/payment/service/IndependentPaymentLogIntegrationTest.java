package com.github.dimitryivaniuta.marketplace.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.marketplace.payment.domain.PaymentOperation;
import com.github.dimitryivaniuta.marketplace.payment.repository.PaymentRepository;
import io.r2dbc.spi.ConnectionFactories;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

/** Verifies that provider-call evidence survives a later parent transaction rollback. */
@Testcontainers
class IndependentPaymentLogIntegrationTest {

    /** Disposable payment database. */
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.4")
                    .withDatabaseName("payment_test")
                    .withUsername("marketplace")
                    .withPassword("marketplace");

    /** Independent logger under test. */
    private static IndependentPaymentLogService independentLogService;
    /** Payment repository. */
    private static PaymentRepository repository;
    /** Parent transaction operator. */
    private static TransactionalOperator transactionalOperator;

    /** Migrates the database and creates production repository objects. */
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
        R2dbcTransactionManager transactionManager = new R2dbcTransactionManager(connectionFactory);
        repository = new PaymentRepository(DatabaseClient.create(connectionFactory));
        transactionalOperator = TransactionalOperator.create(transactionManager);
        independentLogService = new IndependentPaymentLogService(repository, transactionManager);
    }

    /** Clears all payment state between tests. */
    @BeforeEach
    void resetDatabase() throws Exception {
        try (Connection connection = jdbcConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     TRUNCATE TABLE payment_attempt, payment, outbox_event, inbox_event CASCADE
                     """)) {
            statement.execute();
        }
    }

    /** The attempt result commits even when the following payment/outbox transaction fails. */
    @Test
    void shouldKeepProviderOutcomeWhenParentTransactionRollsBack() throws Exception {
        UUID orderId = UUID.randomUUID();
        var payment = repository.createOrLoad(orderId, 15_000L, "PLN", "tok_success").block();
        assertThat(payment).isNotNull();
        UUID attemptId = repository.startAttempt(
                payment.id(), PaymentOperation.AUTHORIZE, orderId + ":authorize:v1").block();
        assertThat(attemptId).isNotNull();
        repository.transition(payment.id(), "NEW", "AUTHORIZATION_UNKNOWN").block();

        independentLogService.record(attemptId, "SUCCEEDED", "pay_reference", null).block();

        Mono<Void> failingParentTransaction = repository.authorizationSucceeded(
                        payment.id(), attemptId, UUID.randomUUID(),
                        "pay_reference", orderId + ":authorize:v1")
                .then(Mono.<Void>error(new IllegalStateException("simulated outbox failure")))
                .as(transactionalOperator::transactional);
        StepVerifier.create(failingParentTransaction)
                .expectErrorMessage("simulated outbox failure")
                .verify();

        assertThat(paymentStatus(payment.id())).isEqualTo("AUTHORIZATION_UNKNOWN");
        assertThat(attempt(attemptId)).isEqualTo(new Attempt("SUCCEEDED", "pay_reference"));
    }

    private String paymentStatus(UUID paymentId) throws Exception {
        try (Connection connection = jdbcConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status FROM payment WHERE id = ?")) {
            statement.setObject(1, paymentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    private Attempt attempt(UUID attemptId) throws Exception {
        try (Connection connection = jdbcConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT status, provider_reference FROM payment_attempt WHERE id = ?
                     """)) {
            statement.setObject(1, attemptId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return new Attempt(resultSet.getString(1), resultSet.getString(2));
            }
        }
    }

    private static Connection jdbcConnection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** @param status outcome status @param reference provider reference */
    private record Attempt(String status, String reference) { }
}
