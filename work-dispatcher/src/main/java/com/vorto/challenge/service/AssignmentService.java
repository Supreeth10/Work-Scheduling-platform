package com.vorto.challenge.service;

import com.vorto.challenge.DTO.LoadAssignmentResponse;

import java.util.UUID;

public interface AssignmentService {
    LoadAssignmentResponse getOrReserveLoad(UUID driverId);               // GET /assignment
//    LoadAssignmentResponse pickupReservedLoad(UUID driverId, UUID loadId); // POST /.../pickup
//    LoadAssignmentResponse rejectReservedLoad(UUID driverId, UUID loadId); // POST /.../reject
}
