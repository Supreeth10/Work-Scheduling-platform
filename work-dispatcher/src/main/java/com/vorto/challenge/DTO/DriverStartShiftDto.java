package com.vorto.challenge.DTO;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Shift start result")
public record DriverStartShiftDto(
        @Schema(example = "44e2e372-c01f-488c-80e5-4bc6e07f3c48") UUID shiftId,
        @Schema(example = "3bfd7de8-3ead-4443-9abd-53dd8cc85ec0") UUID driverId,
        @Schema(type = "string", format = "date-time", example = "2025-10-20T01:48:18.287582Z") Instant startTime
) {
}
