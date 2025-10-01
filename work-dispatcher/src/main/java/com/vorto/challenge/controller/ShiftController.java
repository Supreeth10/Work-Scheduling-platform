package com.vorto.challenge.controller;

import com.vorto.challenge.DTO.StartShiftRequest;
import com.vorto.challenge.model.Shift;
import com.vorto.challenge.service.ShiftService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/drivers")
public class ShiftController {
    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }


    @PostMapping("/{driverId}/shift/start")
    public ResponseEntity<?> startShift(@PathVariable("driverId") UUID driverId, @RequestBody StartShiftRequest body){
        try {
            if (body == null || body.latitude() == null || body.longitude() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "latitude and longitude are required"
                ));
            }
            Shift shift = shiftService.startShift(driverId, body.latitude(), body.longitude());

            // Minimal response payload for UI
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "shiftId",   shift.getId(),
                    "driverId",  shift.getDriver().getId(),
                    "startTime", shift.getStartTime()
            ));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            // e.g., already on shift (active shift exists)
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{driverId}/shift/end")
    public ResponseEntity<?> endShift(@PathVariable UUID driverId) {
        try {
            Shift ended = shiftService.endShift(driverId);
            return ResponseEntity.ok(Map.of(
                    "shiftId", ended.getId(),
                    "driverId", driverId,
                    "endTime", ended.getEndTime()
            ));
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            // Either no active shift, or has active load
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
