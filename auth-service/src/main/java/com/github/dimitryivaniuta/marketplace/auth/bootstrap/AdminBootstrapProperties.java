package com.github.dimitryivaniuta.marketplace.auth.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Optional one-time administrator bootstrap credentials. */
@ConfigurationProperties("marketplace.bootstrap.admin")
public record AdminBootstrapProperties(boolean enabled, String email, String password) { }
