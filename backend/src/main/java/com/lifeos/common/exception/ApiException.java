package com.lifeos.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Business-level failure carrying the HTTP status and a stable machine code.
 * Services throw these instead of leaking persistence or framework exceptions.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException notFound(String what, Object id) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found: " + id);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    /** Optimistic-concurrency clash in the event store — the caller should retry. */
    public static ApiException concurrency(String aggregate, long expected, long actual) {
        return new ApiException(HttpStatus.CONFLICT, "CONCURRENCY_CONFLICT",
                "Aggregate %s expected version %d but store is at %d".formatted(aggregate, expected, actual));
    }
}
