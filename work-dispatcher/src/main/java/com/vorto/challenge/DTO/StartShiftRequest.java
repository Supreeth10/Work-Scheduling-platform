package com.vorto.challenge.DTO;

import jakarta.validation.constraints.NotNull;

public record StartShiftRequest( @NotNull Double latitude,
                                 @NotNull Double longitude) {

}
