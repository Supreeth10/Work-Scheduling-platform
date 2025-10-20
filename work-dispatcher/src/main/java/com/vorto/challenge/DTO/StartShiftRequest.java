package com.vorto.challenge.DTO;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;


@Schema(description = "Start shift request with current location")
public record StartShiftRequest(
        @NotNull @Valid @JsonUnwrapped LocationDto currentLocation
) {}
