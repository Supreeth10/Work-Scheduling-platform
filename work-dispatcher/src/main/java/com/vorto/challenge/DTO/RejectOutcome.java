package com.vorto.challenge.DTO;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Outcome of rejecting a reserved load and ending the shift")
public record RejectOutcome(

        @Schema(example = "4f2c2b1d-5f6b-4a2a-9d8a-0c1e2f3a4b5c") UUID driverId,
        @Schema(example = "1a2b3c4d-1111-2222-3333-444455556666") UUID shiftId,
        @Schema(example = "6b0f5f4d-0b9f-4e28-9a4c-7c9f4c7b9f1c") UUID loadId,

        @Schema(description = "Human-readable outcome code",
                example = "REJECTED_AND_SHIFT_ENDED")
        String result,

        @Schema(type = "string", format = "date-time",
                example = "2025-10-19T16:40:00Z")
        Instant shiftEndedAt
) {}
