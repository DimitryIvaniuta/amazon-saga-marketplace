package com.github.dimitryivaniuta.marketplace.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point for the cart service.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CartServiceApplication {

    /**
     * Starts the service.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
