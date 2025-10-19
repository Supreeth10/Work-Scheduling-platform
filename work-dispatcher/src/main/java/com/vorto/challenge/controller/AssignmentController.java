package com.vorto.challenge.controller;

import com.vorto.challenge.DTO.CompleteStopResult;
import com.vorto.challenge.DTO.LoadAssignmentResponse;
import com.vorto.challenge.DTO.RejectOutcome;
import com.vorto.challenge.service.AssignmentService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/drivers")
public class AssignmentController {
    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }


    // GET /api/drivers/{driverId}/assignment
    @GetMapping("/{driverId}/assignment")
    public ResponseEntity<?> getOrReserve(@PathVariable UUID driverId) {
            LoadAssignmentResponse resp = assignmentService.getOrReserveLoad(driverId);
            if (resp == null) return ResponseEntity.noContent().build();
            return ResponseEntity.ok(resp);
    }


    // POST /api/drivers/{driverId}/loads/{loadId}/stops/complete
    @PostMapping("/{driverId}/loads/{loadId}/stops/complete")
    public ResponseEntity<?> completeNextStop(@PathVariable UUID driverId, @PathVariable UUID loadId) {
            CompleteStopResult resp = assignmentService.completeNextStop (driverId, loadId);
            return ResponseEntity.ok(resp);
    }

    //reject releases the load AND ends the driver's shift. No new assignment.
    @PostMapping("/{driverId}/loads/{loadId}/reject")
    public ResponseEntity<?> reject(@PathVariable UUID driverId, @PathVariable UUID loadId) {
        try {
            RejectOutcome outcome = assignmentService.rejectReservedLoadAndEndShift(driverId, loadId);
            return ResponseEntity.ok(outcome);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
}
