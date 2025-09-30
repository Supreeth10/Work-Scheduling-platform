package com.vorto.challenge.service;

import com.vorto.challenge.DTO.CompleteStopResult;
import com.vorto.challenge.DTO.LoadAssignmentResponse;
import com.vorto.challenge.DTO.RejectOutcome;

import java.util.UUID;

public interface AssignmentService {
    LoadAssignmentResponse getOrReserveLoad(UUID driverId);
    CompleteStopResult completeNextStop(UUID driverId, UUID loadId);
    RejectOutcome rejectReservedLoadAndEndShift(UUID driverId, UUID loadId);
}

