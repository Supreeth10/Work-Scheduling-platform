package com.vorto.challenge.DTO;

import java.time.Instant;
import java.util.UUID;

public record DriverEndShiftDto(
        UUID shiftId,
        UUID driverId,
        Instant endTime
) {
}
