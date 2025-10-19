package com.vorto.challenge.controller;

import com.vorto.challenge.DTO.DriverDto;
import com.vorto.challenge.DTO.DriverStateResponse;
import com.vorto.challenge.DTO.LoginOutcome;
import com.vorto.challenge.DTO.LoginRequest;
import com.vorto.challenge.service.DriverService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/drivers")
public class DriverController {
    private final DriverService driverService;
    public DriverController(DriverService driverService) {
        this.driverService = driverService;
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
    public Optional<DriverDto> get(@PathVariable UUID id) {
        return driverService.get(id);
    }

    @GetMapping("/{id}/state")
    public ResponseEntity<DriverStateResponse> getState(@PathVariable UUID id) {
        return ResponseEntity.ok(driverService.getDriverState(id));
    }

}
