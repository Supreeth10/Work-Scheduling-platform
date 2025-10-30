package com.vorto.challenge.dispatch.model;

import java.util.List;
import java.util.UUID;

/**
 * Candidate assignments for a driver or load.
 */
public record Candidates(
        UUID entityId,
        List<UUID> candidateIds,
        List<Route> routes
) {
}

