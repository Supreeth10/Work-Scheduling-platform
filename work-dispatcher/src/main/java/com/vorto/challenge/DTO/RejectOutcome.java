package com.vorto.challenge.DTO;

import java.time.Instant;
import java.util.UUID;

public record RejectOutcome(
        UUID driverId,
        UUID shiftId,
        UUID loadId,
        String result,           // e.g. "REJECTED_AND_SHIFT_ENDED"
        Instant shiftEndedAt
) {}
