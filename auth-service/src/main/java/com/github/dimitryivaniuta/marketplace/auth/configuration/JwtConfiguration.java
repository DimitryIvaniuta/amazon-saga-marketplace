package com.github.dimitryivaniuta.marketplace.auth.configuration;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Loads the asymmetric signing material once and exposes the encoder and public JWK.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {

    /**
     * Loads the RSA JWK from the configured PKCS#12 keystore.
     *
     * @param properties signing properties
     * @return private/public RSA JWK
     * @throws Exception when key material is invalid
     */
    @Bean
    public RSAKey rsaKey(JwtKeyProperties properties) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = properties.keystore().getInputStream()) {
            keyStore.load(input, properties.storePassword().toCharArray());
        }
        RSAPrivateKey privateKey = (RSAPrivateKey) keyStore.getKey(
                properties.alias(), properties.keyPassword().toCharArray());
        RSAPublicKey publicKey = (RSAPublicKey) keyStore.getCertificate(properties.alias()).getPublicKey();
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.nameUUIDFromBytes(publicKey.getEncoded()).toString())
                .build();
    }

    /** @param rsaKey signing key @return JWT encoder */
    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
    }

    /** @return adaptive password encoder */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
