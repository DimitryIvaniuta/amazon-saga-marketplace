package com.github.dimitryivaniuta.marketplace.common.web;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Ensures each HTTP request has a bounded correlation identifier propagated in
 * both the response and Reactor context for structured diagnostics.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationWebFilter implements WebFilter {

    /** Public correlation header. */
    public static final String HEADER = "X-Correlation-Id";
    /** Reactor context key. */
    public static final String CONTEXT_KEY = "correlationId";

    /**
     * Adds or normalizes a correlation identifier.
     *
     * @param exchange current exchange
     * @param chain downstream chain
     * @return completion signal
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = isSafe(supplied) ? supplied : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        return chain.filter(exchange)
                .contextWrite(context -> context.put(CONTEXT_KEY, correlationId));
    }

    private boolean isSafe(String value) {
        return value != null && value.length() <= 128 && value.matches("[A-Za-z0-9._:-]+");
    }
}
