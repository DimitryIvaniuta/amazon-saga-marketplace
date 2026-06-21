package com.github.dimitryivaniuta.marketplace.payment.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** External payment provider connection and timeout settings. */
@ConfigurationProperties("marketplace.payment.provider")
public record PaymentProviderProperties(String baseUrl, Duration timeout) {
    /** Applies defensive defaults for local execution. */
    public PaymentProviderProperties {
        baseUrl = baseUrl == null ? "http://localhost:8090" : baseUrl;
        timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
    }
}
