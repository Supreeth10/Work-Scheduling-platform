package com.vorto.challenge.controller;

import com.vorto.challenge.DTO.LoginRequest;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.service.DriverService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

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
     * - If driver exists: 200 OK (no body)
     * - If not: create and return 201 Created with driver JSON + Location header
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Driver created = driverService.loginOrCreate(request);
        if (created == null) {
            return ResponseEntity.ok().build(); // 200, empty
        }
        // Created → 201 + body + Location: /api/drivers/{id}
        return ResponseEntity
                .created(URI.create("/api/drivers/" + created.getId()))
                .body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable("id") java.util.UUID id) {
        return driverRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
