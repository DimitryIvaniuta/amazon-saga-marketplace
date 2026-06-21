package com.github.dimitryivaniuta.marketplace.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.marketplace.common.event.WorkflowPayloads;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Audit contract test. */
class AuditContractTest {
    /** Audit records retain their independent source. */
    @Test
    void shouldRetainSource() {
        WorkflowPayloads.Audit audit = new WorkflowPayloads.Audit(
                "payment-service", "AUTHORIZE", "PAYMENT", "1", "UNKNOWN", "timeout", Instant.EPOCH);
        assertThat(audit.source()).isEqualTo("payment-service");
    }
}
