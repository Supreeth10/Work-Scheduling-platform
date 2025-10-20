package com.vorto.challenge.controller;

import com.vorto.challenge.DTO.CreateLoadRequest;
import com.vorto.challenge.DTO.LoadSummaryDto;
import com.vorto.challenge.exception.ErrorResponse;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.service.LoadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/loads")
@Tag(name = "Loads", description = "Admin/General: list, fetch, and create loads")
public class LoadController {

    private final LoadService loadService;

    public LoadController(LoadService loadService) {
        this.loadService = loadService;
    }

    @Operation(
            summary = "List loads (optionally filter by status)",
            description = "Returns all loads, optionally filtered by status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of loads",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = LoadSummaryDto.class)),
                            examples = @ExampleObject(
                                    name = "Mixed statuses",
                                    value = """
                    [
                      {
                        "id": "13b7e525-4079-45a8-aeb3-79dc69342fcb",
                        "status": "IN_PROGRESS",
                        "currentStop": "DROPOFF",
                        "pickup": { "lat": 39.7392, "lng": -104.9903 },
                        "dropoff": { "lat": 38.8339, "lng": -104.8214 },
                        "assignedDriver": { "id": "b7f2c2d5-edce-4eea-9da9-3c8f13b13a70", "name": "sam" }
                      },
                      {
                        "id": "93ea2906-edf3-4d1d-8f84-22849a393a7a",
                        "status": "COMPLETED",
                        "currentStop": "DROPOFF",
                        "pickup": { "lat": 31.4484, "lng": -110.074 },
                        "dropoff": { "lat": 34.2226, "lng": -115.9747 },
                        "assignedDriver": null
                      },
                      {
                        "id": "7bf05341-bc3b-4111-a2f8-afe5ff1cc817",
                        "status": "RESERVED",
                        "currentStop": "PICKUP",
                        "pickup": { "lat": 31.4484, "lng": -110.074 },
                        "dropoff": { "lat": 34.2226, "lng": -115.9747 },
                        "assignedDriver": { "id": "55e1c83d-93bb-4b31-b8c2-80fff3333bf3", "name": "sura" }
                      },
                      {
                        "id": "1e8273b3-625b-4ba1-ac73-1e2b972c6d31",
                        "status": "AWAITING_DRIVER",
                        "currentStop": "PICKUP",
                        "pickup": { "lat": 31.4484, "lng": -110.074 },
                        "dropoff": { "lat": 34.2226, "lng": -115.9747 },
                        "assignedDriver": null
                      },
                      {
                        "id": "d6ec4317-25cd-44e0-bc38-4601f804dd03",
                        "status": "COMPLETED",
                        "currentStop": "DROPOFF",
                        "pickup": { "lat": 31.4484, "lng": -110.074 },
                        "dropoff": { "lat": 34.2226, "lng": -115.9747 },
                        "assignedDriver": null
                      }
                    ]
                    """
                            ))),
            @ApiResponse(responseCode = "400", description = "Invalid query parameter",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Bad status value",
                                    value = """
                    {
                      "code": "VALIDATION_ERROR",
                      "message": "Invalid request parameter",
                      "status": 400,
                      "path": "/api/loads",
                      "correlationId": "542ca7f1-ed12-45e1-9310-39fe98956a64",
                      "timestamp": "2025-10-19T20:15:35.277758-07:00",
                      "details": {
                        "value": "AWAITING_DRIVE",
                        "parameter": "status",
                        "expectedType": "Status"
                      }
                    }
                    """
                            )))
    })
    @GetMapping
    public List<LoadSummaryDto> getAll(
            @Parameter(
                    description = "Optional filter by status",
                    examples = {
                            @ExampleObject(name = "Awaiting", value = "AWAITING_DRIVER"),
                            @ExampleObject(name = "Reserved", value = "RESERVED"),
                            @ExampleObject(name = "In progress", value = "IN_PROGRESS"),
                            @ExampleObject(name = "Completed", value = "COMPLETED")
                    }
            )
            @RequestParam(value = "status", required = false) Load.Status status) {
        return loadService.getAll(status);
    }

    @Operation(summary = "Get a single load by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Load",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LoadSummaryDto.class),
                            examples = @ExampleObject(
                                    name = "Load",
                                    value = """
                    {
                      "id": "d6ec4317-25cd-44e0-bc38-4601f804dd03",
                      "status": "COMPLETED",
                      "currentStop": "DROPOFF",
                      "pickup": { "lat": 31.4484, "lng": -110.074 },
                      "dropoff": { "lat": 34.2226, "lng": -115.9747 },
                      "assignedDriver": null
                    }
                    """
                            ))),
            @ApiResponse(responseCode = "404", description = "Load not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Load not found",
                                    value = """
                    {
                      "code": "LOAD_NOT_FOUND",
                      "message": "Load not found: d6ec4317-25cd-44e0-bc38-004601f804dd",
                      "status": 404,
                      "path": "/api/loads/d6ec4317-25cd-44e0-bc38-4601f804dd",
                      "correlationId": "eb2182b2-95e1-4779-a9c9-3ff23ebe0cf9",
                      "timestamp": "2025-10-19T20:19:10.692568-07:00"
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
                      "path": "/api/loads/d-44e0-bc38-4601f804dd",
                      "correlationId": "8e947f56-e24f-4234-8428-e4c180973249",
                      "timestamp": "2025-10-19T20:19:19.142072-07:00",
                      "details": {
                        "value": "d-44e0-bc38-4601f804dd",
                        "parameter": "id",
                        "expectedType": "UUID"
                      }
                    }
                    """
                            )))
    })
    @GetMapping("/{id}")
    public LoadSummaryDto getOne(@PathVariable UUID id) {
        return loadService.getOne(id);
    }

    @Operation(
            summary = "Create a new load",
            description = "Creates a load with pickup and dropoff coordinates."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = CreateLoadRequest.class),
                    examples = @ExampleObject(
                            name = "Pickup Phoenix, Dropoff Blythe",
                            value = """
            {
              "pickup":  { "lat": 33.4484, "lng": -112.0740 },
              "dropoff": { "lat": 33.6131, "lng": -114.5964 }
            }
            """
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Load created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LoadSummaryDto.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Awaiting driver",
                                            value = """
                        {
                          "id": "1e8273b3-625b-4ba1-ac73-1e2b972c6d31",
                          "status": "AWAITING_DRIVER",
                          "currentStop": "PICKUP",
                          "pickup": { "lat": 31.4484, "lng": -110.074 },
                          "dropoff": { "lat": 34.2226, "lng": -115.9747 },
                          "assignedDriver": null
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "Auto-reserved (example)",
                                            value = """
                        {
                          "id": "62918748-267c-4fd0-b0b1-b963f4460aaa",
                          "status": "RESERVED",
                          "currentStop": "PICKUP",
                          "pickup": { "lat": 31.4484, "lng": -110.074 },
                          "dropoff": { "lat": 34.2226, "lng": -115.9747 },
                          "assignedDriver": { "id": "55e1c83d-93bb-4b31-b8c2-80fff3333bf3", "name": "sura" }
                        }
                        """
                                    )
                            })),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "pickup.lat missing",
                                            value = """
                        {
                          "code": "VALIDATION_ERROR",
                          "message": "Validation failed",
                          "status": 400,
                          "path": "/api/loads",
                          "correlationId": "5fcfa2a1-4dfb-48b4-895b-1a504ca5cd95",
                          "timestamp": "2025-10-19T20:20:30.689109-07:00",
                          "details": { "fields": { "pickup.lat": "lat is required" } }
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "dropoff null",
                                            value = """
                        {
                          "code": "VALIDATION_ERROR",
                          "message": "Validation failed",
                          "status": 400,
                          "path": "/api/loads",
                          "correlationId": "0650bb98-de34-431d-84e7-1aa47eb510e0",
                          "timestamp": "2025-10-19T20:20:43.365215-07:00",
                          "details": { "fields": { "dropoff": "must not be null" } }
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "pickup.lat > 90",
                                            value = """
                        {
                          "code": "VALIDATION_ERROR",
                          "message": "Validation failed",
                          "status": 400,
                          "path": "/api/loads",
                          "correlationId": "b738c769-5251-4612-9ebe-64cf3be71e8a",
                          "timestamp": "2025-10-19T20:20:54.577379-07:00",
                          "details": { "fields": { "pickup.lat": "lat must be <= 90" } }
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "pickup == dropoff",
                                            value = """
                        {
                          "code": "VALIDATION_ERROR",
                          "message": "Validation failed",
                          "status": 400,
                          "path": "/api/loads",
                          "correlationId": "33d4ec54-0030-4b22-95b0-fa830cc11294",
                          "timestamp": "2025-10-19T20:21:14.737355-07:00",
                          "details": { "fields": { "distinctStops": "pickup and dropoff cannot be the same coordinates" } }
                        }
                        """
                                    )
                            }))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoadSummaryDto create(@RequestBody @Valid CreateLoadRequest createLoadRequest) {
        return loadService.create(createLoadRequest);
    }
}
