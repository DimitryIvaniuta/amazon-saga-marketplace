package com.github.dimitryivaniuta.marketplace.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Application entry point for append-only marketplace auditing. */
@SpringBootApplication
public class AuditServiceApplication {
    /** @param args command-line arguments */
    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
