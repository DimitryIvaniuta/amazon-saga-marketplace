package com.github.dimitryivaniuta.marketplace.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Application entry point for the inventory service. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class InventoryServiceApplication {
    /** @param args command-line arguments */
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
