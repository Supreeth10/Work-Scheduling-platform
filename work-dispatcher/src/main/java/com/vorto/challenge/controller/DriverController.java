package com.vorto.challenge.controller;

import com.vorto.challenge.DTO.DriverDto;
import com.vorto.challenge.DTO.DriverStateResponse;
import com.vorto.challenge.DTO.LoginRequest;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.service.DriverService;
import com.vorto.challenge.service.DriverService.LoginOutcome;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {
    private final DriverService driverService;
    private final DriverRepository driverRepository;
    public DriverController(DriverService driverService, DriverRepository driverRepository) {
        this.driverService = driverService;
        this.driverRepository = driverRepository;
    }
    /**
     * POST /api/drivers/login
     * - If driver exists: 200 OK + body (driver JSON)
     * - If created:       201 Created + body (driver JSON) + Location header
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        LoginOutcome outcome = driverService.loginOrCreate(request);
        DriverDto dto = outcome.driver();

        if (outcome.created()) {
            return ResponseEntity
                    .created(URI.create("/api/drivers/" + dto.id()))
                    .body(dto);
        } else {
            return ResponseEntity.ok(dto);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id) {
        return driverService.get(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/state")
    public ResponseEntity<DriverStateResponse> getState(@PathVariable UUID id) {
        return ResponseEntity.ok(driverService.getDriverState(id));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> notFound(EntityNotFoundException ex) {
        return ResponseEntity.status(404).body(new ErrorPayload("NOT_FOUND", ex.getMessage()));
    }

    private record ErrorPayload(String code, String message) {}
}
