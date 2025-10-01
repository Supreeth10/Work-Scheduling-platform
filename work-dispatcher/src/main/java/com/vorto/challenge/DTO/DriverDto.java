package com.vorto.challenge.DTO;

import java.util.UUID;

public record DriverDto(
        UUID id,
        String name,
        boolean onShift,
        Double latitude,
        Double longitude
) {}
