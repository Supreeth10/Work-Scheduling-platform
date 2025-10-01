package com.vorto.challenge.service;

import com.vorto.challenge.model.Shift;
import jakarta.transaction.Transactional;

import java.util.UUID;

public interface ShiftService {
    /**
     * Start a shift for the given driver at (lat, lon).
     * @throws jakarta.persistence.EntityNotFoundException if driver doesn't exist.
     * @throws IllegalStateException if driver is already on shift or has a shift row.
     */
    Shift startShift(UUID driverId, double latitude, double longitude);
    Shift endShift(UUID driverId);


}
