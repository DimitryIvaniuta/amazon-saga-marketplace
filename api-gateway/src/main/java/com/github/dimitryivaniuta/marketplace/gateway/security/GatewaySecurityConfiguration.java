package com.github.dimitryivaniuta.marketplace.gateway.security;

import com.github.dimitryivaniuta.marketplace.common.security.JwtRoleConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Central edge-security policy. Services also verify tokens independently so a
 * bypassed gateway does not become an authorization bypass.
 */
@Configuration(proxyBeanMethods = false)
@EnableReactiveMethodSecurity
public class GatewaySecurityConfiguration {

    /**
     * Configures stateless JWT authentication and public discovery endpoints.
     *
     * @param http reactive security builder
     * @return security filter chain
     */
    @Bean
    public SecurityWebFilterChain gatewaySecurity(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(
                                "/api/auth/register", "/api/auth/login", "/oauth2/jwks",
                                "/api/catalog/**", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtRoleConverter())))
                .build();
    }
}
