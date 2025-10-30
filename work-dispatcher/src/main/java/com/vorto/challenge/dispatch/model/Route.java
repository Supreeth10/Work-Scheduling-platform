package com.vorto.challenge.dispatch.model;

/**
 * Routing information between two points.
 */
public record Route(
        double distanceMeters,
        double durationSeconds,
        double cost
) {
}

