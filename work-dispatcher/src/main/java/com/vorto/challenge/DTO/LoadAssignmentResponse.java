package com.vorto.challenge.DTO;

public record LoadAssignmentResponse(
        String loadId,
        double pickupLat,
        double pickupLng,
        double dropoffLat,
        double dropoffLng,
        String status,   // AWAITING_DRIVER | RESERVED | IN_PROGRESS | COMPLETED
        String nextStop  // PICKUP | DROPOFF
) {}
