package com.github.dimitryivaniuta.marketplace.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Application entry point for shipping fulfillment. */
@SpringBootApplication
public class ShippingServiceApplication {
    /** @param args command-line arguments */
    public static void main(String[] args) {
        SpringApplication.run(ShippingServiceApplication.class, args);
    }
}
