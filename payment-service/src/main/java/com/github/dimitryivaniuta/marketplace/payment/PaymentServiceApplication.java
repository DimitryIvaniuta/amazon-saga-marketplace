package com.github.dimitryivaniuta.marketplace.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Application entry point for the payment service. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentServiceApplication {
    /** @param args command-line arguments */
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
