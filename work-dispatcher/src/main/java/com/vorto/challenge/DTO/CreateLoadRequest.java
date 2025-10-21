package com.vorto.challenge.DTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to create a load with pickup and dropoff coordinates")
public record CreateLoadRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Pickup coordinate")
        @JsonProperty("pickup") @NotNull @Valid
        LocationDto pickup,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Dropoff coordinate")
        @JsonProperty("dropoff") @NotNull @Valid
        LocationDto dropoff
) {
    // Cross-field: pickup and dropoff must differ
    @Schema(hidden = true)
    @JsonIgnore
    @AssertTrue(message = "pickup and dropoff cannot be the same coordinates")
    public boolean isDistinctStops() {
        if (pickup == null || dropoff == null ||
                pickup.lat() == null || pickup.lng() == null ||
                dropoff.lat() == null || dropoff.lng() == null) return true; //  field validators handles nulls
        return !(pickup.lat().equals(dropoff.lat()) && pickup.lng().equals(dropoff.lng()));
    }
}