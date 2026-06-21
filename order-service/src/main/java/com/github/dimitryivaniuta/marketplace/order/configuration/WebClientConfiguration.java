package com.github.dimitryivaniuta.marketplace.order.configuration;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/** Configures bounded downstream HTTP clients. */
@Configuration(proxyBeanMethods = false)
public class WebClientConfiguration {
    /** @param properties downstream settings @return WebClient builder */
    @Bean
    public WebClient.Builder marketplaceWebClientBuilder(DownstreamProperties properties) {
        HttpClient client = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.timeout().toMillis())
                .responseTimeout(properties.timeout());
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(client));
    }
}
