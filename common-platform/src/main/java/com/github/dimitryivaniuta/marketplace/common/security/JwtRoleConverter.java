package com.github.dimitryivaniuta.marketplace.common.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Converts the marketplace {@code roles} claim into Spring Security authorities.
 */
public final class JwtRoleConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    /**
     * Converts roles while preserving the JWT subject as principal name.
     *
     * @param jwt verified token
     * @return reactive authentication
     */
    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        Object rawRoles = jwt.getClaims().getOrDefault("roles", List.of());
        Collection<?> roles = rawRoles instanceof Collection<?> values ? values : List.of();
        var authorities = roles.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
        return Mono.just(new JwtAuthenticationToken(jwt, authorities, jwt.getSubject()));
    }
}
