package com.vorto.challenge.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Map;

@Schema(name = "ErrorResponse", description = "Standard API error payload")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @Schema(description = "Stable application error code",
                allowableValues = {
                "VALIDATION_ERROR",
                "DRIVER_NOT_FOUND",
                "LOAD_NOT_FOUND",
                "DRIVER_LOCATION_UNKNOWN",
                "RESERVATION_EXPIRED",
                "SHIFT_NOT_ACTIVE",
                "ACTIVE_LOAD_PRESENT",
                "SHIFT_ALREADY_ACTIVE",
                "LOAD_STATE_CONFLICT",
                "DATA_INTEGRITY_VIOLATION",
                "ACCESS_DENIED",
                "INTERNAL_ERROR"
        },
                requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @Schema(description = "Human-readable message", requiredMode = Schema.RequiredMode.REQUIRED)
        String message,

        @Schema(description = "HTTP status code", requiredMode = Schema.RequiredMode.REQUIRED)
        int status,

        @Schema(description = "Request path", requiredMode = Schema.RequiredMode.REQUIRED)
        String path,

        @Schema(description = "Correlation ID for tracing")
        String correlationId,

        @Schema(description = "Error timestamp", type = "string", format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime timestamp,

        @Schema(description = "Extra context (validation errors, fields, etc.)")
        Map<String, Object> details) {
}
