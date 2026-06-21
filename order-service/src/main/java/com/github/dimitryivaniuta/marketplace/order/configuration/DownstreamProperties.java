package com.github.dimitryivaniuta.marketplace.order.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param cartBaseUrl cart URL @param catalogBaseUrl catalog URL @param timeout HTTP timeout */
@ConfigurationProperties("marketplace.downstream")
public record DownstreamProperties(String cartBaseUrl, String catalogBaseUrl, Duration timeout) { }
