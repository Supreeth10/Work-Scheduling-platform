package com.vorto.challenge.DTO;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Driver's current or newly-reserved load assignment")
public record LoadAssignmentResponse(
        @Schema(description = "Load ID", example = "6b0f5f4d-0b9f-4e28-9a4c-7c9f4c7b9f1c")
        String loadId,

        @Schema(example = "39.7392") Double pickupLat,
        @Schema(example = "-104.9903") Double pickupLng,
        @Schema(example = "39.8021") Double dropoffLat,
        @Schema(example = "-105.0870") Double dropoffLng,
        @Schema(description = "Load status", allowableValues = {"AWAITING_DRIVER","RESERVED","IN_PROGRESS","COMPLETED"},
                example = "RESERVED")
        String status,
        @Schema(description = "Next stop required", allowableValues = {"PICKUP","DROPOFF"}, example = "PICKUP")
        String nextStop

) {}
