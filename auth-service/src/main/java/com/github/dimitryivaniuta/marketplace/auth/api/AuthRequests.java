package com.github.dimitryivaniuta.marketplace.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Authentication API request contracts. */
public final class AuthRequests {

    private AuthRequests() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Registration request.
     *
     * @param email valid email address
     * @param password password with a practical minimum length
     */
    public record Register(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128) String password) {
    }

    /**
     * Login request.
     *
     * @param email account email
     * @param password plaintext password
     */
    public record Login(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {
    }
}
