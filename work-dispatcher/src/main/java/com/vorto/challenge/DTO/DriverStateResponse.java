package com.vorto.challenge.DTO;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Current state of a driver, including shift and load (if any)")
public record DriverStateResponse(
        @Schema(description = "Driver details") DriverDto driver,
        @Schema(description = "Active shift; null if off-shift", nullable = true) ShiftDto shift,
        @Schema(description = "Active load; null if none", nullable = true) LoadSummaryDto load
) {}
