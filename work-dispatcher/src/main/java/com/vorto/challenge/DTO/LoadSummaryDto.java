package com.vorto.challenge.DTO;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Current load snapshot for a driver")
public record LoadSummaryDto(
        @Schema(example = "d6ec4317-25cd-44e0-bc38-4601f804dd03") UUID id,
        @Schema(
                description = "Load status",
                allowableValues = { "AWAITING_DRIVER", "RESERVED", "IN_PROGRESS", "COMPLETED" },
                example = "IN_PROGRESS"
        )
        String status,
        @Schema(
                description = "Next required stop",
                allowableValues = { "PICKUP", "DROPOFF" },
                example = "DROPOFF"
        )
        String currentStop,
        @Schema(description = "Pickup location") LocationDto pickup,
        @Schema(description = "Dropoff location") LocationDto dropoff,
        @Schema(description = "Assigned driver (null if unassigned)")
        DriverLite assignedDriver
) {
    @Schema(description = "Lightweight driver reference")
    public record DriverLite(@Schema(example = "3bfd7de8-3ead-4443-9abd-53dd8cc85ec0") UUID id,
                             @Schema(example = "rama") String name) {}
}
