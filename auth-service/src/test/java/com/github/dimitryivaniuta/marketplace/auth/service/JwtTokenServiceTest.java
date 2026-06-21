package com.github.dimitryivaniuta.marketplace.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.marketplace.auth.configuration.JwtKeyProperties;
import com.github.dimitryivaniuta.marketplace.auth.domain.UserAccount;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

/** Tests access-token claims and expiry. */
class JwtTokenServiceTest {

    /** Verifies a signed token contains expected claims. */
    @Test
    void shouldIssueToken() throws Exception {
        java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        java.security.KeyPair pair = generator.generateKeyPair();
        RSAKey key = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) pair.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) pair.getPrivate())
                .keyID("test").build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
        JwtKeyProperties properties = new JwtKeyProperties(
                new ClassPathResource("keys/dev-jwt.p12"), "changeit", "changeit", "marketplace-jwt",
                "issuer", "marketplace-api", Duration.ofMinutes(15));
        JwtTokenService service = new JwtTokenService(encoder, properties);

        var token = service.issue(new UserAccount(
                UUID.randomUUID(), "user@example.com", "hash", true, Set.of("CUSTOMER")));

        assertThat(token.accessToken()).isNotBlank();
        assertThat(token.roles()).containsExactly("CUSTOMER");
        assertThat(token.expiresAt()).isAfter(java.time.Instant.now());
    }
}
