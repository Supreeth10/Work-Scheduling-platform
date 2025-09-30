package com.vorto.challenge.DTO;

import java.util.UUID;

public record LoadSummaryDto(
        UUID id,
        String status,           // AWAITING_DRIVER / RESERVED / IN_PROGRESS / COMPLETED
        String currentStop,      // PICKUP / DROPOFF
        LatLng pickup,
        LatLng dropoff,
        DriverLite assignedDriver // null if unassigned
) {
    public record LatLng(double lat, double lng) {}
    public record DriverLite(UUID id, String name) {}
}
