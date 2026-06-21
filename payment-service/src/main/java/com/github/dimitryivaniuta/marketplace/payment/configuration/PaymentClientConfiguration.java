package com.github.dimitryivaniuta.marketplace.payment.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** Creates the isolated HTTP client used for payment-provider calls. */
@Configuration(proxyBeanMethods = false)
public class PaymentClientConfiguration {
    /** @param builder managed builder @param properties provider settings @return provider client */
    @Bean
    public WebClient paymentProviderWebClient(
            WebClient.Builder builder, PaymentProviderProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }
}
