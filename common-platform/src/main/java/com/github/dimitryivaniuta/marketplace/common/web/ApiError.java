package com.github.dimitryivaniuta.marketplace.common.web;

import java.time.Instant;
import java.util.List;

/**
 * Consistent RFC-7807-like API error body.
 *
 * @param timestamp failure time
 * @param status numeric HTTP status
 * @param code stable application code
 * @param message safe human-readable message
 * @param path request path
 * @param correlationId diagnostic correlation identifier
 * @param violations optional validation violations
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        List<String> violations) {
}
