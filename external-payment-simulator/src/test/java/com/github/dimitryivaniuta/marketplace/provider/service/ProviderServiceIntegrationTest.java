package com.github.dimitryivaniuta.marketplace.provider.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.marketplace.common.web.ApiException;
import com.github.dimitryivaniuta.marketplace.provider.api.ProviderContracts;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.test.StepVerifier;

/** End-to-end repository/service tests for provider-side idempotency. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ProviderServiceIntegrationTest {

    /** Disposable provider database. */
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.4")
                    .withDatabaseName("provider_test")
                    .withUsername("marketplace")
                    .withPassword("marketplace");

    /** Provider use case under test. */
    @Autowired
    private ProviderService providerService;

    /** Supplies JDBC and R2DBC properties from the disposable database. */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    /** Repeating an authorization key returns exactly the original provider result. */
    @Test
    void shouldReturnSameAuthorizationForRetry() {
        UUID orderId = UUID.randomUUID();
        String key = orderId + ":authorize:v1";
        ProviderContracts.AuthorizeRequest request =
                new ProviderContracts.AuthorizeRequest(orderId, 12_500L, "PLN", "tok_success");

        var first = providerService.authorize(key, request).block();
        var retry = providerService.authorize(key, request).block();

        assertThat(first).isNotNull();
        assertThat(retry).isEqualTo(first);
        assertThat(first.status()).isEqualTo("AUTHORIZED");
    }

    /** Capture retries are idempotent and cannot charge the same authorization twice. */
    @Test
    void shouldReturnSameCaptureForRetry() {
        UUID orderId = UUID.randomUUID();
        var authorization = providerService.authorize(
                orderId + ":authorize:v1",
                new ProviderContracts.AuthorizeRequest(
                        orderId, 9_900L, "PLN", "tok_success"))
                .block();
        assertThat(authorization).isNotNull();

        String captureKey = orderId + ":capture:v1";
        var first = providerService.capture(captureKey, authorization.paymentId()).block();
        var retry = providerService.capture(captureKey, authorization.paymentId()).block();

        assertThat(first).isNotNull();
        assertThat(retry).isEqualTo(first);
        assertThat(first.status()).isEqualTo("CAPTURED");
    }

    /** A deterministic declined token never creates an authorization. */
    @Test
    void shouldRejectDeclinedToken() {
        UUID orderId = UUID.randomUUID();
        StepVerifier.create(providerService.authorize(
                        orderId + ":authorize:v1",
                        new ProviderContracts.AuthorizeRequest(
                                orderId, 1_000L, "PLN", "tok_declined")))
                .expectError(ApiException.class)
                .verify();
    }
}
