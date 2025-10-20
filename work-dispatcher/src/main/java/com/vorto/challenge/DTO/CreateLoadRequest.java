package com.vorto.challenge.DTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to create a load with pickup and dropoff coordinates")
public record CreateLoadRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Pickup coordinate")
        @NotNull @Valid @JsonProperty("pickup") LatLng pickup,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Dropoff coordinate")
        @NotNull @Valid @JsonProperty("dropoff") LatLng dropoff
) {
    @Schema(description = "Latitude/Longitude pair")
    public record LatLng(
            @Schema(example = "31.4484")
            @NotNull(message = "lat is required")
            @DecimalMin(value = "-90.0",  message = "lat must be >= -90")
            @DecimalMax(value = "90.0",   message = "lat must be <= 90")
            Double lat,
            @Schema(example = "-110.074")
            @NotNull(message = "lng is required")
            @DecimalMin(value = "-180.0", message = "lng must be >= -180")
            @DecimalMax(value = "180.0",  message = "lng must be <= 180")
            Double lng
    ) {}
    // Cross-field: pickup and dropoff must differ
    @Schema(hidden = true)
    @JsonIgnore
    @AssertTrue(message = "pickup and dropoff cannot be the same coordinates")
    public boolean isDistinctStops() {
        if (pickup == null || dropoff == null ||
                pickup.lat == null || pickup.lng == null ||
                dropoff.lat == null || dropoff.lng == null) return true; //  field validators handles nulls
        return !(pickup.lat.equals(dropoff.lat) && pickup.lng.equals(dropoff.lng));
    }
}