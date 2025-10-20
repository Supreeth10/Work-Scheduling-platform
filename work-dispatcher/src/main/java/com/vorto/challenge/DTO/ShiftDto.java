package com.vorto.challenge.DTO;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Active shift snapshot")
public record ShiftDto(
        @Schema(example = "44e2e372-c01f-488c-80e5-4bc6e07f3c48") UUID id,
        @Schema(type = "string", format = "date-time", example = "2025-10-20T01:48:18.287582Z")
        Instant startedAt,
        @Schema(example = "33.4484") Double startLat,
        @Schema(example = "-112.074") Double startLng
) {}