package com.iitsm.exception;

import java.time.Instant;

/** Standardized JSON error payload returned for /api/** failures. */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
