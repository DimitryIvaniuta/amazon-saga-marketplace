package com.github.dimitryivaniuta.marketplace.auth.service;

import com.github.dimitryivaniuta.marketplace.auth.api.AuthRequests;
import com.github.dimitryivaniuta.marketplace.auth.api.AuthResponses;
import com.github.dimitryivaniuta.marketplace.auth.domain.UserAccount;
import com.github.dimitryivaniuta.marketplace.auth.repository.UserAccountRepository;
import com.github.dimitryivaniuta.marketplace.common.web.ApiException;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/** Implements registration and credential authentication. */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** JWT issuer. */
    private final JwtTokenService jwtTokenService;
    /** Password encoder. */
    private final PasswordEncoder passwordEncoder;
    /** User persistence gateway. */
    private final UserAccountRepository repository;
    /** Reactive transaction operator. */
    private final TransactionalOperator transactionalOperator;

    /**
     * Registers a standard customer account.
     *
     * @param request registration request
     * @return registered user
     */
    public Mono<AuthResponses.RegisteredUser> register(AuthRequests.Register request) {
        UUID id = UUID.randomUUID();
        String hash = passwordEncoder.encode(request.password());
        return repository.insert(id, request.email(), hash)
                .flatMap(inserted -> inserted == 1L
                        ? repository.addRole(id, "CUSTOMER").thenReturn(
                                new AuthResponses.RegisteredUser(id, request.email().trim().toLowerCase(Locale.ROOT)))
                        : Mono.error(new ApiException(
                                HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered")))
                .as(transactionalOperator::transactional);
    }

    /**
     * Verifies credentials and issues an access token.
     *
     * @param request login request
     * @return token response
     */
    public Mono<AuthResponses.Token> login(AuthRequests.Login request) {
        return repository.findByEmail(request.email())
                .filter(UserAccount::enabled)
                .filter(user -> passwordEncoder.matches(request.password(), user.passwordHash()))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password")))
                .map(jwtTokenService::issue);
    }
}
