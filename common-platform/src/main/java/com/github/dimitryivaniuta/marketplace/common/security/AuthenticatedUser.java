package com.github.dimitryivaniuta.marketplace.common.security;

import java.util.UUID;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Accessor for the authenticated marketplace user identifier.
 */
public final class AuthenticatedUser {

    private AuthenticatedUser() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Reads and validates the UUID-formatted JWT subject.
     *
     * @return current user identifier
     */
    public static Mono<UUID> id() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(authentication -> UUID.fromString(authentication.getToken().getSubject()))
                .switchIfEmpty(Mono.error(new IllegalStateException("Authenticated JWT user is required")));
    }
}
