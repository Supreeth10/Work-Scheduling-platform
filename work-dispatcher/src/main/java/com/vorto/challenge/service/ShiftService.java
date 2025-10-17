package com.vorto.challenge.service;

import com.vorto.challenge.DTO.DriverEndShiftDto;
import com.vorto.challenge.DTO.DriverStartShiftDto;
import jakarta.persistence.EntityNotFoundException;

import java.util.UUID;

public interface ShiftService {
    /**
     * Start a shift for the given driver at (lat, lon).
     *
     * @throws EntityNotFoundException if driver doesn't exist.
     * @throws IllegalStateException                       if driver is already on shift or has a shift row.
     */
    DriverStartShiftDto startShift(UUID driverId, double latitude, double longitude);

    /**
     * Ends a shift for the given driver.
     *
     * @throws EntityNotFoundException if driver doesn't exist.
     * @throws IllegalStateException   if driver currently on an active load
     */
    DriverEndShiftDto endShift(UUID driverId);


}
