package com.github.dimitryivaniuta.marketplace.payment.messaging;

import tools.jackson.databind.json.JsonMapper;
import com.github.dimitryivaniuta.marketplace.common.event.EventEnvelope;
import com.github.dimitryivaniuta.marketplace.common.event.Topics;
import com.github.dimitryivaniuta.marketplace.payment.service.PaymentService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka adapter for durable payment commands. */
@Component
@RequiredArgsConstructor
public class PaymentCommandListener {
    /** JSON mapper. */
    private final JsonMapper objectMapper;
    /** Payment use cases. */
    private final PaymentService paymentService;

    /** @param json event envelope JSON @throws Exception for retryable failures */
    @KafkaListener(topics = Topics.PAYMENT_COMMANDS, groupId = "payment-service-v1")
    public void onCommand(String json) throws Exception {
        paymentService.handle(objectMapper.readValue(json, EventEnvelope.class))
                .block(Duration.ofSeconds(45));
    }
}
