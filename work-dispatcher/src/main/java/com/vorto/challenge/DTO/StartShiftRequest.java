package com.vorto.challenge.DTO;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record StartShiftRequest(
        @NotNull(message = "latitude is required")
        @DecimalMin(value = "-90.0",  message = "latitude must be >= -90")
        @DecimalMax(value = "90.0",   message = "latitude must be <= 90")
        Double latitude,

        @NotNull(message = "longitude is required")
        @DecimalMin(value = "-180.0", message = "longitude must be >= -180")
        @DecimalMax(value = "180.0",  message = "longitude must be <= 180")
        Double longitude
) {}
