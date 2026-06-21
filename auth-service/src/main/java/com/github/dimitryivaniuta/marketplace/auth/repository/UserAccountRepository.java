package com.github.dimitryivaniuta.marketplace.auth.repository;

import com.github.dimitryivaniuta.marketplace.auth.domain.UserAccount;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Reactive persistence gateway for user accounts and roles. */
@Repository
@RequiredArgsConstructor
public class UserAccountRepository {

    /** Reactive SQL client. */
    private final DatabaseClient databaseClient;

    /**
     * Finds an account with all roles.
     *
     * @param email raw email
     * @return account when present
     */
    public Mono<UserAccount> findByEmail(String email) {
        String normalized = normalize(email);
        Mono<BaseUser> user = databaseClient.sql("""
                SELECT id, email, password_hash, enabled
                  FROM app_user
                 WHERE email = :email
                """)
                .bind("email", normalized)
                .map((row, metadata) -> new BaseUser(
                        row.get("id", UUID.class), row.get("email", String.class),
                        row.get("password_hash", String.class), Boolean.TRUE.equals(row.get("enabled", Boolean.class))))
                .one();
        return user.flatMap(base -> roles(base.id())
                .collect(java.util.stream.Collectors.toUnmodifiableSet())
                .map(roles -> new UserAccount(
                        base.id(), base.email(), base.passwordHash(), base.enabled(), roles)));
    }

    /**
     * Inserts a user account.
     *
     * @param id generated identifier
     * @param email raw email
     * @param passwordHash encoded password
     * @return inserted row count
     */
    public Mono<Long> insert(UUID id, String email, String passwordHash) {
        return databaseClient.sql("""
                INSERT INTO app_user(id, email, password_hash, enabled, created_at, updated_at)
                VALUES (:id, :email, :passwordHash, true, now(), now())
                ON CONFLICT (email) DO NOTHING
                """)
                .bind("id", id)
                .bind("email", normalize(email))
                .bind("passwordHash", passwordHash)
                .fetch()
                .rowsUpdated();
    }

    /**
     * Adds a role idempotently.
     *
     * @param userId user identifier
     * @param role role name
     * @return completion signal
     */
    public Mono<Void> addRole(UUID userId, String role) {
        return databaseClient.sql("""
                INSERT INTO user_role(user_id, role)
                VALUES (:userId, :role)
                ON CONFLICT DO NOTHING
                """)
                .bind("userId", userId)
                .bind("role", role)
                .fetch().rowsUpdated().then();
    }

    private Flux<String> roles(UUID userId) {
        return databaseClient.sql("SELECT role FROM user_role WHERE user_id = :userId")
                .bind("userId", userId)
                .map((row, metadata) -> row.get("role", String.class))
                .all();
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record BaseUser(UUID id, String email, String passwordHash, boolean enabled) {
    }
}
