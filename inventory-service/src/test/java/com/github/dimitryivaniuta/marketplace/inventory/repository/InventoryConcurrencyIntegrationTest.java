package com.github.dimitryivaniuta.marketplace.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.r2dbc.spi.ConnectionFactories;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** PostgreSQL integration tests for no-oversell, rollback, and hot-SKU striping invariants. */
@Testcontainers
class InventoryConcurrencyIntegrationTest {

    /** Disposable PostgreSQL instance matching the production major version. */
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.4")
                    .withDatabaseName("inventory_test")
                    .withUsername("marketplace")
                    .withPassword("marketplace");

    /** Inventory repository under test. */
    private static InventoryRepository repository;
    /** Reactive transaction operator. */
    private static TransactionalOperator transactionalOperator;

    /** Migrates the database and creates the real reactive repository. */
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
        repository = new InventoryRepository(DatabaseClient.create(connectionFactory));
        transactionalOperator = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
    }

    /** Clears mutable state while retaining Flyway history. */
    @BeforeEach
    void resetDatabase() throws Exception {
        try (Connection connection = jdbcConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     TRUNCATE TABLE inventory_reservation_line, inventory_reservation,
                         inventory_bucket, inventory, outbox_event, inbox_event CASCADE
                     """)) {
            statement.execute();
        }
    }

    /** Two buyers cannot reserve eight units each when only ten units exist. */
    @Test
    void shouldPreventConcurrentOverselling() throws Exception {
        UUID skuId = UUID.randomUUID();
        UUID firstOrder = UUID.randomUUID();
        UUID secondOrder = UUID.randomUUID();
        seedInventory(skuId, 10);
        repository.createReservation(firstOrder, UUID.randomUUID(), futureExpiry()).block();
        repository.createReservation(secondOrder, UUID.randomUUID(), futureExpiry()).block();

        List<Long> firstAttempts = Mono.zip(
                        repository.reserveLine(firstOrder, skuId, 8),
                        repository.reserveLine(secondOrder, skuId, 8))
                .map(tuple -> List.of(tuple.getT1(), tuple.getT2()))
                .block();

        assertThat(firstAttempts).isNotNull();
        List<Long> results = retryFailedReservations(
                List.of(firstOrder, secondOrder), firstAttempts, skuId, 8);
        assertThat(results).containsExactlyInAnyOrder(0L, 1L);
        assertThat(stock(skuId)).isEqualTo(new Stock(2, 8, 0));
    }

    /** A failure on a later SKU rolls back all earlier stock changes in the reservation. */
    @Test
    void shouldRollbackAllLinesWhenOneSkuCannotBeReserved() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID availableSku = UUID.randomUUID();
        UUID unavailableSku = UUID.randomUUID();
        seedInventory(availableSku, 5);
        seedInventory(unavailableSku, 0);

        Mono<Void> reservation = repository.createReservation(orderId, UUID.randomUUID(), futureExpiry())
                .then(repository.reserveLine(orderId, availableSku, 2))
                .flatMap(changed -> changed == 1L ? Mono.<Void>empty() : Mono.<Void>error(new IllegalStateException()))
                .then(repository.reserveLine(orderId, unavailableSku, 1))
                .flatMap(changed -> changed == 1L
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(new IllegalStateException("insufficient stock")))
                .as(transactionalOperator::transactional);

        StepVerifier.create(reservation)
                .expectErrorMessage("insufficient stock")
                .verify();

        assertThat(stock(availableSku)).isEqualTo(new Stock(5, 0, 0));
        assertThat(stock(unavailableSku)).isEqualTo(new Stock(0, 0, 0));
        assertThat(reservationCount(orderId)).isZero();
    }

    /** Parallel one-unit reservations spread writes across multiple bucket rows. */
    @Test
    void shouldDistributeHotSkuReservationsAcrossBuckets() throws Exception {
        UUID skuId = UUID.randomUUID();
        seedInventory(skuId, InventoryRepository.DEFAULT_BUCKET_COUNT);
        List<UUID> orders = Flux.range(0, InventoryRepository.DEFAULT_BUCKET_COUNT)
                .map(ignored -> UUID.randomUUID())
                .collectList()
                .block();
        assertThat(orders).isNotNull();
        Flux.fromIterable(orders)
                .flatMap(orderId -> repository.createReservation(
                        orderId, UUID.randomUUID(), futureExpiry()), 16)
                .blockLast();

        List<Long> firstAttempts = Flux.fromIterable(orders)
                .flatMapSequential(orderId -> repository.reserveLine(orderId, skuId, 1), 16)
                .collectList()
                .block();
        assertThat(firstAttempts).isNotNull();
        List<Long> results = retryFailedReservations(orders, firstAttempts, skuId, 1);

        assertThat(results).hasSize(InventoryRepository.DEFAULT_BUCKET_COUNT)
                .allMatch(result -> result == 1L);
        assertThat(stock(skuId)).isEqualTo(new Stock(0, InventoryRepository.DEFAULT_BUCKET_COUNT, 0));
        assertThat(reservedBucketCount(skuId)).isGreaterThan(1);
    }

    private List<Long> retryFailedReservations(
            List<UUID> orders, List<Long> firstAttempts, UUID skuId, int quantity) {
        return java.util.stream.IntStream.range(0, orders.size())
                .mapToObj(index -> firstAttempts.get(index) == 1L
                        ? 1L
                        : repository.reserveLine(orders.get(index), skuId, quantity).block())
                .toList();
    }

    private static Instant futureExpiry() {
        return Instant.now().plus(15, ChronoUnit.MINUTES);
    }

    private void seedInventory(UUID skuId, int available) {
        repository.setAvailable(skuId, available).block();
    }

    private Stock stock(UUID skuId) throws Exception {
        try (Connection connection = jdbcConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COALESCE(SUM(available_quantity), 0),
                            COALESCE(SUM(reserved_quantity), 0),
                            COALESCE(SUM(sold_quantity), 0)
                       FROM inventory_bucket WHERE sku_id = ?
                     """)) {
            statement.setObject(1, skuId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return new Stock(resultSet.getInt(1), resultSet.getInt(2), resultSet.getInt(3));
            }
        }
    }

    private int reservedBucketCount(UUID skuId) throws Exception {
        try (Connection connection = jdbcConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT count(*) FROM inventory_bucket
                      WHERE sku_id = ? AND reserved_quantity > 0
                     """)) {
            statement.setObject(1, skuId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private long reservationCount(UUID orderId) throws Exception {
        try (Connection connection = jdbcConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM inventory_reservation WHERE order_id = ?")) {
            statement.setObject(1, orderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static Connection jdbcConnection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** @param available available units @param reserved reserved units @param sold sold units */
    private record Stock(int available, int reserved, int sold) { }
}
