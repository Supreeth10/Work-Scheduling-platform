package com.vorto.challenge.controller;

import com.vorto.challenge.DTO.LoadAssignmentResponse;
import com.vorto.challenge.service.AssignmentService;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.sql.ast.tree.update.Assignment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

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
        try {
            LoadAssignmentResponse resp = assignmentService.getOrReserveLoad(driverId);
            if (resp == null) return ResponseEntity.noContent().build();
            return ResponseEntity.ok(resp);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

}
