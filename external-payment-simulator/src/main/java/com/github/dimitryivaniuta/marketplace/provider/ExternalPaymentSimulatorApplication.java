package com.github.dimitryivaniuta.marketplace.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Local provider simulator used for repeatable end-to-end payment tests. */
@SpringBootApplication
public class ExternalPaymentSimulatorApplication {
    /** @param args command-line arguments */
    public static void main(String[] args) {
        SpringApplication.run(ExternalPaymentSimulatorApplication.class, args);
    }
}
