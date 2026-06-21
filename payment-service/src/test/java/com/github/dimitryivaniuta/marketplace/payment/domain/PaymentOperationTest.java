package com.github.dimitryivaniuta.marketplace.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.marketplace.payment.service.PaymentService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for stable external idempotency keys. */
class PaymentOperationTest {
    /** Ensures retries use exactly the same operation key. */
    @Test
    void shouldBuildStableOperationKey() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000111");
        assertThat(PaymentService.operationKey(orderId, PaymentOperation.CAPTURE))
                .isEqualTo(orderId + ":capture:v1");
    }
}
