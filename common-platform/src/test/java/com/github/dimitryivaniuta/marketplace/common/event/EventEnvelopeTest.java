package com.github.dimitryivaniuta.marketplace.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

/** Tests immutable event-envelope invariants. */
class EventEnvelopeTest {

    /** Verifies generated events contain stable mandatory metadata. */
    @Test
    void shouldCreateVersionedEvent() {
        EventEnvelope event = EventEnvelope.create(
                "test.created.v1", "correlation", null, JsonMapper.builder().build().createObjectNode());

        assertThat(event.eventId()).isNotNull();
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.occurredAt()).isNotNull();
    }

    /** Verifies invalid schema versions are rejected immediately. */
    @Test
    void shouldRejectInvalidVersion() {
        assertThatThrownBy(() -> new EventEnvelope(
                java.util.UUID.randomUUID(), "event", "correlation", null,
                java.time.Instant.now(), 0, JsonMapper.builder().build().createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
