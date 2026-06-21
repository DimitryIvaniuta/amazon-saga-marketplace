package com.github.dimitryivaniuta.marketplace.inventory.scheduling;

import com.github.dimitryivaniuta.marketplace.inventory.service.InventoryService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases checkout reservations that exceeded their hold period. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryJob {
    /** Maximum duration of one expiry pass. */
    private static final Duration SCAN_TIMEOUT = Duration.ofSeconds(30);
    /** Inventory service. */
    private final InventoryService inventoryService;

    /** Scans for expired holds and keeps the task attached during shutdown. */
    @Scheduled(fixedDelayString = "${marketplace.inventory.expiry-scan-delay:5s}")
    public void expire() {
        try {
            inventoryService.expireReservations().block(SCAN_TIMEOUT);
        } catch (RuntimeException exception) {
            log.error("Inventory expiry scan failed", exception);
        }
    }
}
