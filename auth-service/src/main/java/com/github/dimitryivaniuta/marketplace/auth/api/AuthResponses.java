package com.github.dimitryivaniuta.marketplace.auth.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Authentication API response contracts. */
public final class AuthResponses {

    private AuthResponses() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Registration result.
     *
     * @param userId generated user identifier
     * @param email normalized email
     */
    public record RegisteredUser(UUID userId, String email) {
    }

    /**
     * Bearer token result.
     *
     * @param accessToken signed JWT
     * @param tokenType token type
     * @param expiresAt expiry timestamp
     * @param roles granted roles
     */
    public record Token(String accessToken, String tokenType, Instant expiresAt, Set<String> roles) {
    }
}
