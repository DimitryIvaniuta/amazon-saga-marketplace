package com.github.dimitryivaniuta.marketplace.order.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Records end-to-end checkout Saga latency with bounded terminal-state labels. */
@Component
public class OrderSagaMetrics {

    /** Metrics registry. */
    private final MeterRegistry registry;

    /** @param registry metrics registry */
    public OrderSagaMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Records elapsed time from durable order creation to a terminal Saga state.
     *
     * @param duration end-to-end Saga duration
     * @param terminalState completed, cancelled, or manual intervention
     */
    public void terminal(Duration duration, String terminalState) {
        Timer.builder("marketplace.order.saga.duration")
                .description("End-to-end checkout Saga duration")
                .tag("terminal_state", terminalState)
                .register(registry)
                .record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }
}
