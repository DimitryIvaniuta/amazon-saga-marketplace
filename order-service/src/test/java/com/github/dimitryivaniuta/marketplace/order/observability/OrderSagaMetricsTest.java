package com.github.dimitryivaniuta.marketplace.order.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Tests bounded end-to-end Saga latency metrics. */
class OrderSagaMetricsTest {

    /** A terminal transition records exactly one timer sample. */
    @Test
    void shouldRecordTerminalSagaDuration() {
        var registry = new SimpleMeterRegistry();
        var metrics = new OrderSagaMetrics(registry);

        metrics.terminal(Duration.ofSeconds(2), "completed");

        var timer = registry.find("marketplace.order.saga.duration")
                .tag("terminal_state", "completed")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.SECONDS)).isBetween(1.999, 2.001);
    }
}
