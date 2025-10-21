package com.vorto.challenge.DTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Driver's current or newly-reserved load assignment")
public record LoadAssignmentResponse(
        @Schema(description = "Load ID", example = "6b0f5f4d-0b9f-4e28-9a4c-7c9f4c7b9f1c")
        String loadId,

        @Schema(description = "Load pick up location")
        @Valid @NotNull
        LocationDto pickup,
        @Schema(description = "drop off location")
        @Valid @NotNull
        LocationDto dropoff,

        @Schema(description = "Load status", allowableValues = {"AWAITING_DRIVER","RESERVED","IN_PROGRESS","COMPLETED"},
                example = "RESERVED")
        String status,
        @Schema(description = "Next stop required", allowableValues = {"PICKUP","DROPOFF"}, example = "PICKUP")
        String nextStop

) {}
