package com.vorto.challenge.dispatch.model;

import org.locationtech.jts.geom.Point;

import java.util.UUID;

/**
 * Immutable snapshot of load state for optimization.
 */
public record LoadSnapshot(
        UUID loadId,
        Point pickup,
        Point dropoff,
        String status
) {
}

