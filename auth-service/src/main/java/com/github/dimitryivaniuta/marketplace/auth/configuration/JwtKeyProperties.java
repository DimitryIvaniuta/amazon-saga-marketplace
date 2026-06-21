package com.github.dimitryivaniuta.marketplace.auth.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Externalized JWT signing-key and token-lifetime configuration.
 * Production deployments must mount the keystore through a secret manager.
 *
 * @param keystore PKCS#12 resource
 * @param storePassword keystore password
 * @param keyPassword private-key password
 * @param alias key alias
 * @param issuer expected token issuer
 * @param audience expected marketplace audience
 * @param accessTokenTtl access-token lifetime
 */
@ConfigurationProperties("marketplace.security.jwt")
public record JwtKeyProperties(
        Resource keystore,
        String storePassword,
        String keyPassword,
        String alias,
        String issuer,
        String audience,
        Duration accessTokenTtl) {
}
