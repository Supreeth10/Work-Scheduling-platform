package com.vorto.challenge.DTO;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Driver summary")
public record DriverDto(
        @Schema(example = "3bfd7de8-3ead-4443-9abd-53dd8cc85ec0") UUID id,
        @Schema(example = "rama") String name,
        @Schema(description = "Whether a shift is active") boolean onShift,
        @Schema(description = "Last known latitude; null if off-shift", example = "31.4484") Double latitude,
        @Schema(description = "Last known longitude; null if off-shift", example = "-110.074") Double longitude
) {}
