package com.vorto.challenge.DTO;

public record LoadAssignmentResponse(
        String loadId,
        Double pickupLat,
        Double pickupLng,
        Double dropoffLat,
        Double dropoffLng,
        String status,   // AWAITING_DRIVER | RESERVED | IN_PROGRESS | COMPLETED
        String nextStop  // PICKUP | DROPOFF
) {}
