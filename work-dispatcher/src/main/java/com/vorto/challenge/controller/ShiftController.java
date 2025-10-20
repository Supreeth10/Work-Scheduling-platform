package com.vorto.challenge.controller;

import com.vorto.challenge.DTO.DriverEndShiftDto;
import com.vorto.challenge.DTO.DriverStartShiftDto;
import com.vorto.challenge.DTO.StartShiftRequest;
import com.vorto.challenge.exception.ErrorResponse;
import com.vorto.challenge.service.ShiftService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/drivers")
@Tag(name = "Shifts", description = "Driver shift lifecycle: start and end")
public class ShiftController {
    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    /**
     * POST /api/drivers/{driverId}/shift/start
     */
    @Operation(
            summary = "Start a shift at the driver's current location",
            description = "Begins an indefinite shift. Location provided as latitude/longitude."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Shift started",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DriverStartShiftDto.class),
                            examples = @ExampleObject(
                                    name = "Shift started",
                                    value = """
                    {
                      "shiftId": "44e2e372-c01f-488c-80e5-4bc6e07f3c48",
                      "driverId": "3bfd7de8-3ead-4443-9abd-53dd8cc85ec0",
                      "startTime": "2025-10-20T01:48:18.287582Z"
                    }
                    """
                            ))),

            // 409 — SHIFT_ALREADY_ACTIVE
            @ApiResponse(responseCode = "409", description = "Driver already on shift",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Shift already active",
                                    value = """
                    {
                      "code": "SHIFT_ALREADY_ACTIVE",
                      "message": "Driver is already on shift.",
                      "status": 409,
                      "path": "/api/drivers/3bfd7de8-3ead-4443-9abd-53dd8cc85ec0/shift/start",
                      "correlationId": "eda85d2e-e3a7-4021-9fae-e880029e5c5d",
                      "timestamp": "2025-10-19T19:00:59.741088-07:00"
                    }
                    """
                            ))),

            // 400 — VALIDATION_ERROR
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Body validation error",
                                    value = """
                    {
                      "code": "VALIDATION_ERROR",
                      "message": "Validation failed",
                      "status": 400,
                      "path": "/api/drivers/55e1c83d-93bb-4b31-b8c2-80fff3333bf3/shift/start",
                      "correlationId": "3569f31c-22c8-43f8-bb38-bb92a924e7af",
                      "timestamp": "2025-10-19T19:01:30.81794-07:00",
                      "details": {
                        "fields": {
                          "latitude": "latitude must be <= 90"
                        }
                      }
                    }
                    """
                            ))),

            // 404 — DRIVER_NOT_FOUND
            @ApiResponse(responseCode = "404", description = "Driver not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Driver not found",
                                    value = """
                    {
                      "code": "DRIVER_NOT_FOUND",
                      "message": "Driver not found: 55e1c83d-93bb-4b31-b8c2-80fff3333bf7",
                      "status": 404,
                      "path": "/api/drivers/55e1c83d-93bb-4b31-b8c2-80fff3333bf7/shift/start",
                      "correlationId": "1892e379-b255-4c5c-bfc5-9f017e3776e2",
                      "timestamp": "2025-10-19T19:02:32.437378-07:00"
                    }
                    """
                            )))
    })
    @PostMapping("/{driverId}/shift/start")
    public ResponseEntity<?> startShift(
            @PathVariable("driverId") UUID driverId,
            @Valid @RequestBody StartShiftRequest body){
            DriverStartShiftDto driverStartShiftDto = shiftService.startShift(driverId, body.latitude(), body.longitude());
            return ResponseEntity.status(HttpStatus.CREATED).body(driverStartShiftDto);
    }
    /**
     * POST /api/drivers/{driverId}/shift/end
     */
    @Operation(
            summary = "End the driver's current active shift",
            description = "Ends the active shift. Fails if a load is still active (reserved/in progress)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shift ended",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DriverEndShiftDto.class),
                            examples = @ExampleObject(
                                    name = "Shift ended",
                                    value = """
                    {
                      "shiftId": "c8aa58bf-cb5f-476f-8dae-32668eb5a134",
                      "driverId": "55e1c83d-93bb-4b31-b8c2-80fff3333bf3",
                      "endTime": "2025-10-20T02:02:57.488604Z"
                    }
                    """
                            ))),

            // 409 — ACTIVE_LOAD_PRESENT / SHIFT_NOT_ACTIVE
            @ApiResponse(responseCode = "409", description = "Conflict (active load or off-shift)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Active load present",
                                            value = """
                        {
                          "code": "ACTIVE_LOAD_PRESENT",
                          "message": "Cannot end shift: driver has an active load",
                          "status": 409,
                          "path": "/api/drivers/55e1c83d-93bb-4b31-b8c2-80fff3333bf3/shift/end",
                          "correlationId": "98a4ab51-42cf-437f-b93d-86ee8d3751cf",
                          "timestamp": "2025-10-19T19:03:44.060057-07:00"
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "Shift not active",
                                            value = """
                        {
                          "code": "SHIFT_NOT_ACTIVE",
                          "message": "Driver is off-shift",
                          "status": 409,
                          "path": "/api/drivers/55e1c83d-93bb-4b31-b8c2-80fff3333bf3/shift/end",
                          "correlationId": "11a47b18-3488-4f1d-a77d-fa8bb4c26aa0",
                          "timestamp": "2025-10-19T19:04:25.417268-07:00"
                        }
                        """
                                    )
                            })),

            // 404 — DRIVER_NOT_FOUND
            @ApiResponse(responseCode = "404", description = "Driver not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Driver not found",
                                    value = """
                    {
                      "code": "DRIVER_NOT_FOUND",
                      "message": "Driver not found: 3bfd7de8-3ead-4443-9abd-53dd8cc85ec1",
                      "status": 404,
                      "path": "/api/drivers/3bfd7de8-3ead-4443-9abd-53dd8cc85ec1/shift/end",
                      "correlationId": "f0b3a893-7190-4cd5-8a6c-2992f3982463",
                      "timestamp": "2025-10-19T19:08:09.026738-07:00"
                    }
                    """
                            ))),

            // 400 — VALIDATION_ERROR
            @ApiResponse(responseCode = "400", description = "Invalid request parameter",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Path parameter validation error",
                                    value = """
                    {
                      "code": "VALIDATION_ERROR",
                      "message": "Invalid request parameter",
                      "status": 400,
                      "path": "/api/drivers/3bfd7de83ead-4443-9abd-53dd8cc85ec1/shift/end",
                      "correlationId": "c004af50-8543-4002-a470-e8a86cecf1d8",
                      "timestamp": "2025-10-19T19:08:25.094665-07:00",
                      "details": {
                        "expectedType": "UUID",
                        "parameter": "driverId",
                        "value": "3bfd7de83ead-4443-9abd-53dd8cc85ec1"
                      }
                    }
                    """
                            )))
    })
    @PostMapping("/{driverId}/shift/end")
    public ResponseEntity<?> endShift(@PathVariable UUID driverId) {
            DriverEndShiftDto endedShift = shiftService.endShift(driverId);
            return ResponseEntity.ok(endedShift);
    }
}
