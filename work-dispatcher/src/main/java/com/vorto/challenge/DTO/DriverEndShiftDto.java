package com.vorto.challenge.DTO;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Shift end result")
public record DriverEndShiftDto(
        @Schema(example = "c8aa58bf-cb5f-476f-8dae-32668eb5a134") UUID shiftId,
        @Schema(example = "55e1c83d-93bb-4b31-b8c2-80fff3333bf3") UUID driverId,
        @Schema(type = "string", format = "date-time", example = "2025-10-20T02:02:57.488604Z") Instant endTime
) {
}
