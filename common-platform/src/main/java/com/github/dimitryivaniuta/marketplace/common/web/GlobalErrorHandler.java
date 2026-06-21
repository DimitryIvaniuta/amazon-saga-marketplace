package com.github.dimitryivaniuta.marketplace.common.web;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Last-resort reactive error handler that avoids leaking stack traces or provider details.
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    /** JSON serializer. */
    private final JsonMapper objectMapper;

    /**
     * Converts known failures to safe JSON and logs unexpected errors with correlation data.
     *
     * @param exchange failed exchange
     * @param throwable failure
     * @return response write completion
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String code = "INTERNAL_ERROR";
        String message = "An unexpected error occurred";
        List<String> violations = List.of();

        if (throwable instanceof ApiException apiException) {
            status = apiException.getStatus();
            code = apiException.getCode();
            message = apiException.getMessage();
        } else if (throwable instanceof WebExchangeBindException bindException) {
            status = HttpStatus.BAD_REQUEST;
            code = "VALIDATION_FAILED";
            message = "Request validation failed";
            violations = bindException.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .toList();
        } else {
            log.error("Unhandled request failure path={}", exchange.getRequest().getPath(), throwable);
        }

        String correlationId = exchange.getResponse().getHeaders().getFirst(CorrelationWebFilter.HEADER);
        ApiError body = new ApiError(
                Instant.now(), status.value(), code, message,
                exchange.getRequest().getPath().value(), correlationId, violations);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JacksonException exception) {
            bytes = "{\"code\":\"INTERNAL_ERROR\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}
