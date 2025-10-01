package com.vorto.challenge.DTO;

import java.time.Instant;
import java.util.UUID;

public record ShiftDto(
        UUID id,
        Instant startedAt,
        Double startLat,
        Double startLng
) {}