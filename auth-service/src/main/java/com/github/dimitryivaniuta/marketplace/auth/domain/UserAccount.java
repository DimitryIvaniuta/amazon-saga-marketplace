package com.github.dimitryivaniuta.marketplace.auth.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Authenticated user aggregate loaded for credential verification.
 *
 * @param id user identifier
 * @param email normalized email
 * @param passwordHash BCrypt hash
 * @param enabled account enabled flag
 * @param roles application roles
 */
public record UserAccount(UUID id, String email, String passwordHash, boolean enabled, Set<String> roles) {
}
