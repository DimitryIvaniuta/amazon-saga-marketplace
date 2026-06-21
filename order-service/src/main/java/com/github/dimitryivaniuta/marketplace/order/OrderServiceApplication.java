package com.github.dimitryivaniuta.marketplace.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Application entry point for the order service. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class OrderServiceApplication {
    /** @param args command-line arguments */
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
