package com.vorto.challenge.DTO;

import java.time.Instant;
import java.util.UUID;

public record DriverStartShiftDto(
        UUID shiftId,
        UUID driverId,
        Instant startTime
) {
}
