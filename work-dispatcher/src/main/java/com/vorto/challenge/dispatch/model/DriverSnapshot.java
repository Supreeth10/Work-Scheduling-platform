package com.vorto.challenge.dispatch.model;

import org.locationtech.jts.geom.Point;

import java.util.UUID;

/**
 * Immutable snapshot of driver state for optimization.
 */
public record DriverSnapshot(
        UUID driverId,
        UUID shiftId,
        Point currentLocation,
        boolean available
) {
}

