package com.occ.housecleaning.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error body. {@code code} is a machine-readable business code (e.g. DUPLICATE_TRADE)
 * and is null for generic HTTP errors.
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> validationErrors,
        String code) {
}
