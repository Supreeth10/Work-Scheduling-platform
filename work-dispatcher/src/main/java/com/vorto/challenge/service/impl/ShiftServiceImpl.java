package com.vorto.challenge.service.impl;

import com.vorto.challenge.DTO.DriverEndShiftDto;
import com.vorto.challenge.DTO.DriverStartShiftDto;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Shift;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.LoadRepository;
import com.vorto.challenge.repository.ShiftRepository;
import com.vorto.challenge.service.ShiftService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import static com.vorto.challenge.JtsGeo.point;

import java.time.Instant;
import java.util.UUID;


@Service
public class ShiftServiceImpl implements ShiftService {

    private final DriverRepository driverRepository;
    private final ShiftRepository shiftRepository;
    private final LoadRepository loadRepository;

    public ShiftServiceImpl(DriverRepository driverRepository, ShiftRepository shiftRepository, LoadRepository loadRepository) {
        this.driverRepository = driverRepository;
        this.shiftRepository = shiftRepository;
        this.loadRepository = loadRepository;
    }
    /**
     * Starts a new shift for the given driver at the provided coordinates.
     * Enforces "one active shift per driver" and updates the driver's state.
     */
    @Override
    @Transactional
    public DriverStartShiftDto startShift(UUID driverId, double latitude, double longitude) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + driverId));

        // Guard against duplicates: either an existing shift row or onShift flag already true
        if (shiftRepository.existsByDriverIdAndEndTimeIsNull(driverId) || driver.isOnShift()) {
            throw new IllegalStateException("Driver is already on shift.");
        }
        Point startPoint = point(latitude, longitude);

        // Update driver state
        driver.setOnShift(true);
        driver.setCurrentLocation(startPoint);

        // Create new Shift
        Shift newShift = new Shift();
        newShift.setDriver(driver);
        newShift.setStartLocation(startPoint);
        newShift.setStartTime(Instant.now());

        // Persist in a single transaction
        driverRepository.save(driver);
        shiftRepository.save(newShift);

        return new DriverStartShiftDto(newShift.getId(),driver.getId(),newShift.getStartTime());

    }

    /**
     * Ends the driver's current active shift.
     * Disallows ending a shift if the driver still has an active (RESERVED/IN_PROGRESS) load.
     */
    @Override
    @Transactional
    public DriverEndShiftDto endShift(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + driverId));

        // Must have an active shift
        Shift activeShift = shiftRepository.findFirstByDriverIdAndEndTimeIsNull(driverId)
                .orElseThrow(() -> new IllegalStateException("Driver has no active shift."));

        // Block if driver has an active load
        if (loadRepository.existsActiveByDriverId(driverId)) {
            throw new IllegalStateException("Cannot end shift: driver has an active load.");
        }

        // Close shift & flip flag
        activeShift.setEndTime(Instant.now());
        driver.setOnShift(false);
        driver.setCurrentLocation(null);

        // Persist
        shiftRepository.save(activeShift);
        driverRepository.save(driver);

        return new DriverEndShiftDto(activeShift.getId(),driver.getId(),activeShift.getEndTime());
    }
}
