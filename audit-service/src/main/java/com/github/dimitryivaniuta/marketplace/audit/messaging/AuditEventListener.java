package com.github.dimitryivaniuta.marketplace.audit.messaging;

import tools.jackson.databind.json.JsonMapper;
import com.github.dimitryivaniuta.marketplace.audit.repository.AuditRepository;
import com.github.dimitryivaniuta.marketplace.common.event.EventEnvelope;
import com.github.dimitryivaniuta.marketplace.common.event.Topics;
import com.github.dimitryivaniuta.marketplace.common.event.WorkflowPayloads;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka sink that persists immutable audit messages in its own database. */
@Component
@RequiredArgsConstructor
public class AuditEventListener {
    /** Repository. */
    private final AuditRepository auditRepository;
    /** JSON mapper. */
    private final JsonMapper objectMapper;

    /** @param json event JSON @throws Exception on malformed input */
    @KafkaListener(topics = Topics.AUDIT_EVENTS, groupId = "audit-service-v1")
    public void onAudit(String json) throws Exception {
        EventEnvelope event = objectMapper.readValue(json, EventEnvelope.class);
        WorkflowPayloads.Audit audit = objectMapper.convertValue(event.payload(), WorkflowPayloads.Audit.class);
        auditRepository.append(event, audit).block(Duration.ofSeconds(20));
    }
}
