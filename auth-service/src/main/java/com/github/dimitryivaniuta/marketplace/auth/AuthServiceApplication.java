package com.github.dimitryivaniuta.marketplace.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point for the auth service.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthServiceApplication {

    /**
     * Starts the service.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
