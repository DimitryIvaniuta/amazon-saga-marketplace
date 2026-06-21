package com.github.dimitryivaniuta.marketplace.common.web;

import org.springframework.http.HttpStatus;

/**
 * Expected API failure carrying a stable machine-readable code.
 */
public class ApiException extends RuntimeException {

    /** HTTP response status. */
    private final HttpStatus status;
    /** Stable client-facing error code. */
    private final String code;

    /**
     * Creates an API exception.
     *
     * @param status HTTP status
     * @param code stable error code
     * @param message safe error message
     */
    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** @return stable error code */
    public String getCode() {
        return code;
    }

    /** @return HTTP status */
    public HttpStatus getStatus() {
        return status;
    }
}
