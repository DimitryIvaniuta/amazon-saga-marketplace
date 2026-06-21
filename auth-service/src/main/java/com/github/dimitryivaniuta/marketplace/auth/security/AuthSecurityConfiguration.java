package com.github.dimitryivaniuta.marketplace.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/** Authentication-service endpoint policy. */
@Configuration(proxyBeanMethods = false)
public class AuthSecurityConfiguration {

    /**
     * Allows registration, login and public key discovery while protecting all other endpoints.
     *
     * @param http security builder
     * @return security chain
     */
    @Bean
    public SecurityWebFilterChain authSecurity(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/auth/register", "/api/auth/login", "/oauth2/jwks",
                                "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .anyExchange().denyAll())
                .build();
    }
}
