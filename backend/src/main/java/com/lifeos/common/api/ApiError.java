package com.lifeos.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The single error envelope every LifeOS service returns. Keeping one shape means
 * the web client can render any failure without special-casing per endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors,
        List<String> details
) {
    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, null, null);
    }

    public static ApiError validation(String path, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), 400, "VALIDATION_FAILED",
                "Request payload failed validation", path, fieldErrors, null);
    }
}
