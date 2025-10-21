package com.vorto.challenge.controller;

import com.vorto.challenge.DTO.DriverDto;
import com.vorto.challenge.DTO.DriverStateResponse;
import com.vorto.challenge.DTO.LoginOutcome;
import com.vorto.challenge.DTO.LoginRequest;
import com.vorto.challenge.exception.ErrorResponse;
import com.vorto.challenge.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Drivers", description = "Driver login and state queries")
public class DriverController {
    private final DriverService driverService;
    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }
    /**
     * POST /api/drivers/login
     * - If driver exists: 200 OK + body (driver JSON)
     * - If created: 201 Created + body (driver JSON) + Location header
     */
    @Operation(
            summary = "Login or create driver",
            description = "If the username exists, returns the driver (200). Otherwise creates a new driver and returns 201 with Location."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver exists",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DriverDto.class),
                            examples = @ExampleObject(
                                    name = "Existing driver",
                                    value = """
                                            {
                                                 "id": "b1b63042-f1ce-4054-b914-40be3662c16f",
                                                 "name": "surat",
                                                 "onShift": true,
                                                 "currentLocation": {
                                                     "lat": 33.44,
                                                     "lng": -112.07
                                                 }
                                             }
                    """
                            ))),

            @ApiResponse(responseCode = "201", description = "Driver created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DriverDto.class),
                            examples = @ExampleObject(
                                    name = "New driver",
                                    value = """
                                            {
                                                 "id": "dd4de4d7-edfb-437d-b803-0e93fdc12f86",
                                                 "name": "alexis",
                                                 "onShift": false,
                                                 "currentLocation": null
                                             }
                    """
                            ))),

            // 400 — VALIDATION_ERROR (two variants)
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Missing username",
                                            value = """
                        {
                          "code": "VALIDATION_ERROR",
                          "message": "Validation failed",
                          "status": 400,
                          "path": "/api/drivers/login",
                          "correlationId": "00fcff24-ed62-4e82-9657-d4927d0a90c5",
                          "timestamp": "2025-10-19T19:48:55.980924-07:00",
                          "details": { "fields": { "username": "username is required" } }
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "Invalid characters",
                                            value = """
                        {
                          "code": "VALIDATION_ERROR",
                          "message": "Validation failed",
                          "status": 400,
                          "path": "/api/drivers/login",
                          "correlationId": "1b97b1fd-7b7f-4b28-85f5-29e03ae02608",
                          "timestamp": "2025-10-19T19:50:21.232553-07:00",
                          "details": { "fields": { "username": "username contains invalid characters" } }
                        }
                        """
                                    )
                            }))
    })
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

    /**
     * GET /api/drivers/{id}
     */
    @Operation(summary = "Get a driver by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DriverDto.class),
                            examples = @ExampleObject(
                                    name = "Driver",
                                    value = """
                                            {
                                                 "id": "b1b63042-f1ce-4054-b914-40be3662c16f",
                                                 "name": "surat",
                                                 "onShift": true,
                                                 "currentLocation": {
                                                     "lat": 33.44,
                                                     "lng": -112.07
                                                 }
                                             }
                    """
                            ))),

            @ApiResponse(responseCode = "404", description = "Driver not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Driver not found",
                                    value = """
                    {
                      "code": "DRIVER_NOT_FOUND",
                      "message": "Driver not found: 194e1e4a-296d-4f35-b174-c5a3db524fe1",
                      "status": 404,
                      "path": "/api/drivers/194e1e4a-296d-4f35-b174-c5a3db524fe1",
                      "correlationId": "2b358916-4943-45a3-9d01-3160a38926c8",
                      "timestamp": "2025-10-19T19:51:42.321767-07:00"
                    }
                    """
                            ))),

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
                      "path": "/api/drivers/194e1e4a296d-4f35-b174-c5a3db524fee",
                      "correlationId": "f59645f8-33f0-4ec4-a00c-21e8ac132445",
                      "timestamp": "2025-10-19T19:51:52.936072-07:00",
                      "details": {
                        "expectedType": "UUID",
                        "parameter": "id",
                        "value": "194e1e4a296d-4f35-b174-c5a3db524fee"
                      }
                    }
                    """
                            )))
    })
    @GetMapping("/{id}")
    public Optional<DriverDto> get(@PathVariable UUID id) {
        return driverService.get(id);
    }

    /**
     * GET /api/drivers/{id}/state
     */
    @Operation(summary = "Get the driver's current state (driver/shift/load)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver state",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DriverStateResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Load in progress",
                                            value = """
                                                    {
                                                         "driver": {
                                                             "id": "b1b63042-f1ce-4054-b914-40be3662c16f",
                                                             "name": "surat",
                                                             "onShift": true,
                                                             "currentLocation": {
                                                                 "lat": 39.7392,
                                                                 "lng": -104.9903
                                                             }
                                                         },
                                                         "shift": {
                                                             "id": "2b5f0af7-0b62-4e6b-bec5-331c57e6dc27",
                                                             "startedAt": "2025-10-21T03:12:52.838809Z",
                                                             "startLocation": {
                                                                 "lat": 33.44,
                                                                 "lng": -112.07
                                                             }
                                                         },
                                                         "load": {
                                                             "id": "d881f618-2058-492e-8106-ffc1ef2edcdf",
                                                             "status": "IN_PROGRESS",
                                                             "currentStop": "DROPOFF",
                                                             "pickup": {
                                                                 "lat": 39.7392,
                                                                 "lng": -104.9903
                                                             },
                                                             "dropoff": {
                                                                 "lat": 33.4484,
                                                                 "lng": -112.074
                                                             },
                                                             "assignedDriver": {
                                                                 "id": "b1b63042-f1ce-4054-b914-40be3662c16f",
                                                                 "name": "surat"
                                                             }
                                                         }
                                                     }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "On shift, no load",
                                            value = """
                        {
                          "driver": {
                            "id": "3bfd7de8-3ead-4443-9abd-53dd8cc85ec0",
                            "name": "rama",
                            "onShift": true,
                            "currentLocation": {
                                                                 "lat": 39.7392,
                                                                 "lng": -104.9903
                                                             }
                          },
                          "shift": {
                            "id": "44e2e372-c01f-488c-80e5-4bc6e07f3c48",
                            "startedAt": "2025-10-20T01:48:18.287582Z",
                            "startLocation": {
                                                                 "lat": 33.44,
                                                                 "lng": -112.07
                                                             }
                          },
                          "load": null
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "Off shift",
                                            value = """
                                                    {
                                                         "driver": {
                                                             "id": "b1b63042-f1ce-4054-b914-40be3662c16f",
                                                             "name": "surat",
                                                             "onShift": false,
                                                             "currentLocation": null
                                                         },
                                                         "shift": null,
                                                         "load": null
                                                     }
                        """
                                    )
                            })),

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
                      "path": "/api/drivers/3bfd7de8-3ead-4443-9abd-53dd8cc85ec1/state",
                      "correlationId": "446cb1ac-d18e-432b-a53f-ac589bbf0eee",
                      "timestamp": "2025-10-19T19:54:33.535887-07:00"
                    }
                    """
                            ))),

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
                      "path": "/api/drivers/194e1e4a296d-4f35-b174-c5a3db524fee/state",
                      "correlationId": "1bfcb86a-17fa-4abc-9140-2de46426acf7",
                      "timestamp": "2025-10-19T19:52:23.661408-07:00",
                      "details": {
                        "expectedType": "UUID",
                        "parameter": "id",
                        "value": "194e1e4a296d-4f35-b174-c5a3db524fee"
                      }
                    }
                    """
                            )))
    })
    @GetMapping("/{id}/state")
    public ResponseEntity<DriverStateResponse> getState(@PathVariable UUID id) {
        return ResponseEntity.ok(driverService.getDriverState(id));
    }

}
