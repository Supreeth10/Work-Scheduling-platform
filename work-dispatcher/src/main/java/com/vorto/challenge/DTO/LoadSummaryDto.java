package com.vorto.challenge.DTO;

import java.util.UUID;

public record LoadSummaryDto(
        UUID id,
        String status,           // AWAITING_DRIVER / RESERVED / IN_PROGRESS / COMPLETED
        String currentStop,      // PICKUP / DROPOFF
        LocationDto pickup,
        LocationDto dropoff,
        DriverLite assignedDriver // null if unassigned
) {
    public record DriverLite(UUID id, String name) {}
}
