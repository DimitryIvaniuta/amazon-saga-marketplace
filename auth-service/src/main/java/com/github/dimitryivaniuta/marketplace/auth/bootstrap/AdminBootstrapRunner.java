package com.github.dimitryivaniuta.marketplace.auth.bootstrap;

import com.github.dimitryivaniuta.marketplace.auth.repository.UserAccountRepository;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;

/** Creates a local administrator only when explicitly enabled by configuration. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {
    /** Bootstrap settings. */
    private final AdminBootstrapProperties properties;
    /** Password encoder. */
    private final PasswordEncoder passwordEncoder;
    /** User repository. */
    private final UserAccountRepository repository;
    /** Transaction operator. */
    private final TransactionalOperator transactionalOperator;

    /** @param args application arguments */
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (properties.email() == null || properties.password() == null
                || properties.password().length() < 12) {
            throw new IllegalStateException("Enabled administrator bootstrap requires email and a 12+ character password");
        }
        repository.findByEmail(properties.email())
                .switchIfEmpty(repository.insert(UUID.randomUUID(), properties.email(),
                                passwordEncoder.encode(properties.password()))
                        .flatMap(ignored -> repository.findByEmail(properties.email())))
                .flatMap(user -> repository.addRole(user.id(), "ADMIN")
                        .then(repository.addRole(user.id(), "CUSTOMER")))
                .as(transactionalOperator::transactional)
                .block(Duration.ofSeconds(20));
        log.info("Administrator bootstrap checked for {}", properties.email());
    }
}
