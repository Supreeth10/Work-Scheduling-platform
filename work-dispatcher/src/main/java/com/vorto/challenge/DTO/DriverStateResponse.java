package com.vorto.challenge.DTO;

public record DriverStateResponse(
        DriverDto driver,
        ShiftDto shift,          // null if off-shift
        LoadSummaryDto load      // null if no active load
) {}
