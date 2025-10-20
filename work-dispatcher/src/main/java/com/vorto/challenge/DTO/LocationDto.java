package com.vorto.challenge.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Geographic point")
public record LocationDto(
        @Schema(example = "33.4484")
        @NotNull(message = "lat is required")
        @DecimalMin(value = "-90.0",  message = "lat must be >= -90")
        @DecimalMax(value = "90.0",   message = "lat must be <= 90")
        Double lat,

        @Schema(example = "-112.0740")
        @NotNull(message = "lng is required")
        @DecimalMin(value = "-180.0", message = "lng must be >= -180")
        @DecimalMax(value = "180.0",  message = "lng must be <= 180")
        Double lng) {
}
