package com.github.dimitryivaniuta.marketplace.auth.api;

import com.github.dimitryivaniuta.marketplace.auth.service.AuthService;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Authentication and JWK discovery HTTP API. */
@RestController
@RequiredArgsConstructor
public class AuthController {

    /** Authentication use cases. */
    private final AuthService authService;
    /** Current signing key. */
    private final RSAKey rsaKey;

    /**
     * Registers a customer.
     *
     * @param request registration data
     * @return created user
     */
    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AuthResponses.RegisteredUser> register(@Valid @RequestBody AuthRequests.Register request) {
        return authService.register(request);
    }

    /**
     * Authenticates credentials.
     *
     * @param request login data
     * @return bearer token
     */
    @PostMapping("/api/auth/login")
    public Mono<AuthResponses.Token> login(@Valid @RequestBody AuthRequests.Login request) {
        return authService.login(request);
    }

    /**
     * Publishes only the RSA public key in standard JWK-set form.
     *
     * @return public JWK set
     */
    @GetMapping("/oauth2/jwks")
    public Map<String, Object> jwks() {
        return Map.of("keys", java.util.List.of(rsaKey.toPublicJWK().toJSONObject()));
    }
}
