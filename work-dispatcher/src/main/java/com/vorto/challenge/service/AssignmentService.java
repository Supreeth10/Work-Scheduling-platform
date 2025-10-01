package com.vorto.challenge.service;

import com.vorto.challenge.DTO.CompleteStopResult;
import com.vorto.challenge.DTO.LoadAssignmentResponse;
import com.vorto.challenge.DTO.RejectOutcome;

import java.util.UUID;

public interface AssignmentService {
    LoadAssignmentResponse getOrReserveLoad(UUID driverId);
    CompleteStopResult completeNextStop(UUID driverId, UUID loadId);
    RejectOutcome rejectReservedLoadAndEndShift(UUID driverId, UUID loadId);
    /**
     * Called after a load is created.
     * Attempts to assign/reserve this load to the closest on-shift driver
     * who currently has no RESERVED/IN_PROGRESS load.
     * No-op if none available.
     */
    void tryAssignNewlyCreatedLoad(UUID loadId);
}

