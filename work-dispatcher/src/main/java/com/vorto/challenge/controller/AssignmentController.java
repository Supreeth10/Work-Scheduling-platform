package com.vorto.challenge.controller;

import com.vorto.challenge.DTO.CompleteStopResult;
import com.vorto.challenge.DTO.LoadAssignmentResponse;
import com.vorto.challenge.DTO.RejectOutcome;
import com.vorto.challenge.exception.ErrorResponse;
import com.vorto.challenge.service.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;



import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/drivers")
@Tag(name = "Assignments", description = "Driver assignment lifecycle: view, complete next stop, reject load")
public class AssignmentController {
    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }


    /**
     * GET /api/drivers/{driverId}/assignment
     */
    @Operation(
            summary = "Get current assignment or reserve one for on-shift driver",
            description = """
      Returns the driver's current load assignment if present; if the driver is on shift and unassigned,
      the server may reserve an eligible load and return it. If nothing is available, returns 204 No Content.
      """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assignment found or reserved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LoadAssignmentResponse.class),
                            examples = @ExampleObject(
                                    name = "Reserved example",
                                    value = """
                                            {
                                                 "loadId": "eefe1c0f-6bed-4d15-beb7-58273fbffa0d",
                                                 "pickup": {
                                                     "lat": 39.7392,
                                                     "lng": -104.9903
                                                 },
                                                 "dropoff": {
                                                     "lat": 38.8339,
                                                     "lng": -104.8214
                                                 },
                                                 "status": "RESERVED",
                                                 "nextStop": "PICKUP"
                                             }
                """
                            ))),
            @ApiResponse(responseCode = "204", description = "No assignment available"),

            // 400 — VALIDATION_ERROR
            @ApiResponse(responseCode = "400", description = "Invalid request parameter",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Validation error",
                                    value = """
                {
                  "code": "VALIDATION_ERROR",
                  "message": "Invalid request parameter",
                  "status": 400,
                  "path": "/api/drivers/39d040a9-e99f-46d2-93c3-72ccd2cfaab1dd3e/assignment",
                  "correlationId": "c36d8a75-9404-4512-b8af-14c34656dc12",
                  "timestamp": "2025-10-19T18:07:03.309885-07:00",
                  "details": {
                    "parameter": "driverId",
                    "value": "39d040a9-e99f-46d2-93c3-72ccd2cfaab1dd3e",
                    "expectedType": "UUID"
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
                  "message": "Driver not found: 39d040a9-e99f-46d2-93c3-72ccd2cfaab1",
                  "status": 404,
                  "path": "/api/drivers/39d040a9-e99f-46d2-93c3-72ccd2cfaab1/assignment",
                  "correlationId": "8baedd67-d322-40f3-9ca9-0103a7098606",
                  "timestamp": "2025-10-19T18:06:53.870178-07:00"
                }
                """
                            ))),

            // 409 — SHIFT_NOT_ACTIVE
            @ApiResponse(responseCode = "409", description = "Off-shift driver (no active shift)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Off-shift driver",
                                    value = """
                {
                  "code": "SHIFT_NOT_ACTIVE",
                  "message": "Driver is off-shift",
                  "status": 409,
                  "path": "/api/drivers/39d040a9-e99f-46d2-93c3-72ccd2cfaab2/assignment",
                  "correlationId": "dcfc157a-8537-4612-a318-19dafd483e53",
                  "timestamp": "2025-10-19T17:56:30.95248-07:00"
                }
                """
                            )))
    })
    @GetMapping("/{driverId}/assignment")
    public ResponseEntity<?> getOrReserve(@PathVariable UUID driverId) {
            LoadAssignmentResponse resp = assignmentService.getOrReserveLoad(driverId);
            if (resp == null) return ResponseEntity.noContent().build();
            return ResponseEntity.ok(resp);
    }


    /**
     * POST /api/drivers/{driverId}/loads/{loadId}/stops/complete
     */
    @Operation(
            summary = "Complete the next stop (PICKUP or DROPOFF) for a load",
            description = """
            Marks the next required stop as completed for the specified load assigned to the driver.
            Returns the updated assignment and, if the load completed at DROPOFF, an optional next auto-assigned load.
            """
    )
    @ApiResponses({
            // 200 — success (your two success examples)
            @ApiResponse(responseCode = "200", description = "Stop completed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CompleteStopResult.class),
                            examples = {
                                    @ExampleObject(
                                            name = "After completing PICKUP",
                                            value = """
                                                    {
                                                           "completed": {
                                                               "loadId": "772d5d11-8210-4bcf-81b0-787789c743f7",
                                                               "pickup": {
                                                                   "lat": 33.44,
                                                                   "lng": -112.07
                                                               },
                                                               "dropoff": {
                                                                   "lat": 34.2226,
                                                                   "lng": -115.9747
                                                               },
                                                               "status": "COMPLETED",
                                                               "nextStop": "DROPOFF"
                                                           },
                      "nextAssignment": null
                    }
                    """
                                    ),
                                    @ExampleObject(
                                            name = "After completing DROPOFF (load completed)",
                                            value = """
                                                    {
                                                         "completed": {
                                                             "loadId": "772d5d11-8210-4bcf-81b0-787789c743f7",
                                                             "pickup": {
                                                                 "lat": 33.44,
                                                                 "lng": -112.07
                                                             },
                                                             "dropoff": {
                                                                 "lat": 34.2226,
                                                                 "lng": -115.9747
                                                             },
                                                             "status": "COMPLETED",
                                                             "nextStop": "DROPOFF"
                                                         },
                                                         "nextAssignment": {
                                                             "loadId": "d53cbc87-b485-47e7-aa86-552da503df41",
                                                             "pickup": {
                                                                 "lat": 40.01499,
                                                                 "lng": -105.27055
                                                             },
                                                             "dropoff": {
                                                                 "lat": 39.7392,
                                                                 "lng": -104.9903
                                                             },
                                                             "status": "RESERVED",
                                                             "nextStop": "PICKUP"
                                                         }
                                                     }
                    """
                                    )
                            })),

            // 400 — VALIDATION_ERROR (bad UUID, etc.)
            @ApiResponse(responseCode = "400", description = "Invalid request parameter",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Validation error",
                                    value = """
                {
                  "code": "VALIDATION_ERROR",
                  "message": "Invalid request parameter",
                  "status": 400,
                  "path": "/api/drivers/39d040a9-e99f-46d2-93c3-72ccd2cfaab1dd3e/loads/00018391-3978-4860-8c70-1becede052d0/stops/complete",
                  "correlationId": "3195e6f8-94d3-449c-afa8-5a51ead495e6",
                  "timestamp": "2025-10-19T18:29:57.286211-07:00",
                  "details": {
                    "value": "39d040a9-e99f-46d2-93c3-72ccd2cfaab1dd3e",
                    "parameter": "driverId",
                    "expectedType": "UUID"
                  }
                }
                """
                            ))),

            // 403 — ACCESS_DENIED (load not assigned to this driver)
            @ApiResponse(responseCode = "403", description = "Load not assigned to this driver",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Access denied",
                                    value = """
                {
                  "code": "ACCESS_DENIED",
                  "message": "Load not assigned to this driver",
                  "status": 403,
                  "path": "/api/drivers/39d040a9-e99f-46d2-93c3-72ccd2cfaab2/loads/f7127afb-0e62-4c94-802d-3dbd18751727/stops/complete",
                  "correlationId": "d6306bf8-7868-49e3-8190-0f96b496c386",
                  "timestamp": "2025-10-19T18:33:08.531945-07:00"
                }
                """
                            ))),

            // 404 — DRIVER_NOT_FOUND or LOAD_NOT_FOUND (two examples under same 404)
            @ApiResponse(responseCode = "404", description = "Driver or load not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Driver not found",
                                            value = """
                    {
                      "code": "DRIVER_NOT_FOUND",
                      "message": "Driver not found: 39d040a9-e99f-46d2-93c3-72ccd2cfaab6",
                      "status": 404,
                      "path": "/api/drivers/39d040a9-e99f-46d2-93c3-72ccd2cfaab6/loads/fb2bff5d-90c2-4c39-be30-41d3e7923633/stops/complete",
                      "correlationId": "444267da-ce72-4402-900a-c001bc61664a",
                      "timestamp": "2025-10-19T18:35:58.805809-07:00"
                    }
                    """
                                    ),
                                    @ExampleObject(
                                            name = "Load not found",
                                            value = """
                    {
                      "code": "LOAD_NOT_FOUND",
                      "message": "Load not found: fb2bff5d-90c2-4c39-be30-41d3e7923637",
                      "status": 404,
                      "path": "/api/drivers/39d040a9-e99f-46d2-93c3-72ccd2cfaab2/loads/fb2bff5d-90c2-4c39-be30-41d3e7923637/stops/complete",
                      "correlationId": "c7eb80aa-6f4f-4abf-abf8-037885f5a85d",
                      "timestamp": "2025-10-19T18:36:25.07551-07:00"
                    }
                    """
                                    )
                            })),

            // 409 — SHIFT_NOT_ACTIVE or RESERVATION_EXPIRED (two examples under same 409)
            @ApiResponse(responseCode = "409", description = "Conflict (driver off-shift or reservation expired)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Shift not active",
                                            value = """
                    {
                      "code": "SHIFT_NOT_ACTIVE",
                      "message": "Driver is off-shift",
                      "status": 409,
                      "path": "/api/drivers/3bfd7de8-3ead-4443-9abd-53dd8cc85ec0/loads/f7127afb-0e62-4c94-802d-3dbd18751727/stops/complete",
                      "correlationId": "ae78cbcc-ab25-4aa1-ab5d-f9c80bff4dc1",
                      "timestamp": "2025-10-19T18:34:36.249207-07:00"
                    }
                    """
                                    ),
                                    @ExampleObject(
                                            name = "Reservation expired",
                                            value = """
                    {
                      "code": "RESERVATION_EXPIRED",
                      "message": "Reservation expired. Fetch assignment again.",
                      "status": 409,
                      "path": "/api/drivers/39d040a9-e99f-46d2-93c3-72ccd2cfaab2/loads/fb2bff5d-90c2-4c39-be30-41d3e7923633/stops/complete",
                      "correlationId": "2e5d8c06-48ab-452a-9c20-d5bda87dd96a",
                      "timestamp": "2025-10-19T18:38:21.784094-07:00"
                    }
                    """
                                    )
                            }))
    })
    @PostMapping("/{driverId}/loads/{loadId}/stops/complete")
    public ResponseEntity<?> completeNextStop(@PathVariable UUID driverId, @PathVariable UUID loadId) {
            CompleteStopResult resp = assignmentService.completeNextStop (driverId, loadId);
            return ResponseEntity.ok(resp);
    }

    /**
     * POST /api/drivers/{driverId}/loads/{loadId}/reject
     * Reject releases the load AND ends the driver's shift. No new assignment.
     */
    @Operation(
            summary = "Reject reserved load and end driver shift",
            description = "Unassigns the reserved load and immediately ends the driver's current shift."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Load rejected and shift ended",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = RejectOutcome.class),
                            examples = @ExampleObject(
                                    name = "Rejected and shift ended",
                                    value = """
                {
                  "driverId": "4f2c2b1d-5f6b-4a2a-9d8a-0c1e2f3a4b5c",
                  "shiftId": "1a2b3c4d-1111-2222-3333-444455556666",
                  "loadId": "6b0f5f4d-0b9f-4e28-9a4c-7c9f4c7b9f1c",
                  "result": "REJECTED_AND_SHIFT_ENDED",
                  "shiftEndedAt": "2025-10-19T16:40:00Z"
                }
                """
                            ))),

            // 400 — VALIDATION_ERROR (bad UUID, etc.)
            @ApiResponse(responseCode = "400", description = "Invalid request parameter",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Validation error",
                                    value = """
                {
                  "code": "VALIDATION_ERROR",
                  "message": "Invalid request parameter",
                  "status": 400,
                  "path": "/api/drivers/3bfd7de83ead-4443-9abd-53dd8cc85ec3/loads/7fa024c9-ae54-49b6-a6d6-247c42fe49b1/reject",
                  "correlationId": "0417198c-098a-43fe-8818-d8159f3e0ba3",
                  "timestamp": "2025-10-19T18:50:01.744414-07:00",
                  "details": {
                    "expectedType": "UUID",
                    "value": "3bfd7de83ead-4443-9abd-53dd8cc85ec3",
                    "parameter": "driverId"
                  }
                }
                """
                            ))),

            // 403 — ACCESS_DENIED (load not assigned to this driver)
            @ApiResponse(responseCode = "403", description = "Load not assigned to this driver",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Access denied",
                                    value = """
                {
                  "code": "ACCESS_DENIED",
                  "message": "Load not assigned to this driver",
                  "status": 403,
                  "path": "/api/drivers/39d040a9-e99f-46d2-93c3-72ccd2cfaab2/loads/00018391-3978-4860-8c70-1becede052d0/reject",
                  "correlationId": "ea0dee8d-4cbe-4496-9bbb-53684c503ed8",
                  "timestamp": "2025-10-18T23:19:38.341775-07:00",
                  "details": null
                }
                """
                            ))),

            // 404 — DRIVER_NOT_FOUND or LOAD_NOT_FOUND (two examples)
            @ApiResponse(responseCode = "404", description = "Driver or load not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Driver not found",
                                            value = """
                    {
                      "code": "DRIVER_NOT_FOUND",
                      "message": "Driver not found: 7fa024c9-ae54-49b6-a6d6-247c42fe49b1",
                      "status": 404,
                      "path": "/api/drivers/3bfd7de8-3ead-4443-9abd-53dd8cc85ec0/loads/7fa024c9-ae54-49b6-a6d6-247c42fe49b1/reject",
                      "correlationId": "6993d42f-e281-4929-ad1c-78a814a73ebf",
                      "timestamp": "2025-10-19T18:48:44.636348-07:00"
                    }
                    """
                                    ),
                                    @ExampleObject(
                                            name = "Load not found",
                                            value = """
                    {
                      "code": "LOAD_NOT_FOUND",
                      "message": "Load not found: 7fa024c9-ae54-49b6-a6d6-247c42fe49b1",
                      "status": 404,
                      "path": "/api/drivers/3bfd7de8-3ead-4443-9abd-53dd8cc85ec0/loads/7fa024c9-ae54-49b6-a6d6-247c42fe49b1/reject",
                      "correlationId": "6993d42f-e281-4929-ad1c-78a814a73ebf",
                      "timestamp": "2025-10-19T18:48:44.636348-07:00"
                    }
                    """
                                    )
                            })),

            // 409 — LOAD_STATE_CONFLICT (must be RESERVED) — plus any other conflicts
            @ApiResponse(responseCode = "409", description = "Conflict (invalid load state)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Load state conflict",
                                    value = """
                {
                  "code": "LOAD_STATE_CONFLICT",
                  "message": "Only reserved loads can be rejected",
                  "status": 409,
                  "path": "/api/drivers/3bfd7de8-3ead-4443-9abd-53dd8cc85ec0/loads/fb2bff5d-90c2-4c39-be30-41d3e7923633/reject",
                  "correlationId": "c710969a-fd30-4fbe-bfe1-2f7748bbac15",
                  "timestamp": "2025-10-19T18:46:54.853584-07:00"
                }
                """
                            )))
    })
    @PostMapping("/{driverId}/loads/{loadId}/reject")
    public ResponseEntity<?> reject(@PathVariable UUID driverId, @PathVariable UUID loadId) {
            RejectOutcome outcome = assignmentService.rejectReservedLoadAndEndShift(driverId, loadId);
            return ResponseEntity.ok(outcome);
    }
}
