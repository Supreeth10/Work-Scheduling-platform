package com.vorto.challenge.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateLoadRequest(
        @NotNull @Valid LatLng pickup,
        @NotNull @Valid LatLng dropoff
) {
    public record LatLng(
            @NotNull Double lat,
            @NotNull Double lng
    ) {}
}