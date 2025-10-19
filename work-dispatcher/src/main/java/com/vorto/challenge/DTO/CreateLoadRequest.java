package com.vorto.challenge.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CreateLoadRequest(
        @NotNull @Valid @JsonProperty("pickup") LatLng pickup,
        @NotNull @Valid @JsonProperty("dropoff") LatLng dropoff
) {
    public record LatLng(
            @NotNull(message = "lat is required")
            @DecimalMin(value = "-90.0",  message = "lat must be >= -90")
            @DecimalMax(value = "90.0",   message = "lat must be <= 90")
            Double lat,
            @NotNull(message = "lng is required")
            @DecimalMin(value = "-180.0", message = "lng must be >= -180")
            @DecimalMax(value = "180.0",  message = "lng must be <= 180")
            Double lng
    ) {}
    // Cross-field: pickup and dropoff must differ
    @AssertTrue(message = "pickup and dropoff cannot be the same coordinates")
    public boolean isDistinctStops() {
        if (pickup == null || dropoff == null ||
                pickup.lat == null || pickup.lng == null ||
                dropoff.lat == null || dropoff.lng == null) return true; //  field validators handles nulls
        return !(pickup.lat.equals(dropoff.lat) && pickup.lng.equals(dropoff.lng));
    }
}