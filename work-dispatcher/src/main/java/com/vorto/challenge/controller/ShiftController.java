package com.vorto.challenge.controller;

import com.vorto.challenge.DTO.DriverEndShiftDto;
import com.vorto.challenge.DTO.DriverStartShiftDto;
import com.vorto.challenge.DTO.StartShiftRequest;
import com.vorto.challenge.service.ShiftService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/drivers")
public class ShiftController {
    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }


    @PostMapping("/{driverId}/shift/start")
    public ResponseEntity<?> startShift(
            @PathVariable("driverId") UUID driverId,
            @Valid @RequestBody StartShiftRequest body){
            DriverStartShiftDto driverStartShiftDto = shiftService.startShift(driverId, body.latitude(), body.longitude());
            return ResponseEntity.status(HttpStatus.CREATED).body(driverStartShiftDto);
    }

    @PostMapping("/{driverId}/shift/end")
    public ResponseEntity<?> endShift(@PathVariable UUID driverId) {
        try {
            DriverEndShiftDto endedShift = shiftService.endShift(driverId);
            return ResponseEntity.ok(endedShift);
        } catch (EntityNotFoundException e) {
            //if driver doesnt exist
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            // Either no active shift, or has active load
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
