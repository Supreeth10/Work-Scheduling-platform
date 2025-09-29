package com.vorto.challenge.service;

import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Shift;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.ShiftRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import static com.vorto.challenge.JtsGeo.point;

import java.time.Instant;
import java.util.UUID;


@Service
public class ShiftServiceImpl implements ShiftService{

    private final DriverRepository driverRepository;
    private final ShiftRepository shiftRepository;

    public ShiftServiceImpl(DriverRepository driverRepository, ShiftRepository shiftRepository) {
        this.driverRepository = driverRepository;
        this.shiftRepository = shiftRepository;
    }
    /**
     * Starts a new shift for the driver at (latitude, longitude).
     * Rules:
     *  - Driver must exist.
     *  - No active shift should exist (endTime IS NULL).
     *  - Creates a NEW Shift row (history kept), sets driver.onShift = true and updates currentLocation.
     */
    @Override
    @Transactional
    public Shift startShift(UUID driverId, double latitude, double longitude) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + driverId));

        // Guard against duplicates: either an existing shift row or onShift flag already true
        if (shiftRepository.existsByDriverIdAndEndTimeIsNull(driverId) || driver.isOnShift()) {
            throw new IllegalStateException("Driver is already on shift.");
        }
        Point startPoint = point(latitude, longitude);

        // Create new Shift
        Shift shift = new Shift();
        shift.setDriver(driver);
        shift.setStartLocation(startPoint);
        shift.setStartTime(Instant.now());
        // Update driver state
        driver.setOnShift(true);
        driver.setCurrentLocation(startPoint);
        // Persist in a single transaction
        Shift saved = shiftRepository.save(shift);
        driverRepository.save(driver);

        return saved;

    }
}
