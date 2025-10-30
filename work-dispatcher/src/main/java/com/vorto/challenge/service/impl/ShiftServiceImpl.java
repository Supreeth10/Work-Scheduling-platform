package com.vorto.challenge.service.impl;

import com.vorto.challenge.DTO.DriverEndShiftDto;
import com.vorto.challenge.DTO.DriverStartShiftDto;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Shift;
import com.vorto.challenge.optimization.OptimizationTrigger;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.LoadRepository;
import com.vorto.challenge.repository.ShiftRepository;
import com.vorto.challenge.service.ShiftService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static com.vorto.challenge.common.JtsGeo.point;

import java.time.Instant;
import java.util.UUID;


@Service
public class ShiftServiceImpl implements ShiftService {
    private static final Logger log = LoggerFactory.getLogger(ShiftServiceImpl.class);

    private final DriverRepository driverRepository;
    private final ShiftRepository shiftRepository;
    private final LoadRepository loadRepository;
    private final AssignmentServiceImpl assignmentService;

    public ShiftServiceImpl(
            DriverRepository driverRepository, 
            ShiftRepository shiftRepository, 
            LoadRepository loadRepository,
            AssignmentServiceImpl assignmentService) {
        this.driverRepository = driverRepository;
        this.shiftRepository = shiftRepository;
        this.loadRepository = loadRepository;
        this.assignmentService = assignmentService;
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
        boolean hasActiveShift = shiftRepository.existsByDriverIdAndEndTimeIsNull(driverId) || driver.isOnShift();
        if (hasActiveShift) {
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
        
        // Trigger optimization to potentially assign loads to this new driver
        try {
            log.info("Driver {} started shift, triggering optimization", driverId);
            assignmentService.optimizeAndAssign(OptimizationTrigger.DRIVER_SHIFT_START, driverId);
        } catch (Exception e) {
            log.error("Optimization failed after driver shift start", e);
            // Don't fail shift start if optimization fails
        }

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
        Shift activeShift = shiftRepository.findByDriverIdAndEndTimeIsNull(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Driver is off-shift"));

        // Block if driver has an active load
        if (loadRepository.existsActiveByDriverId(driverId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot end shift: driver has an active load");
        }

        // Close shift & flip flag
        activeShift.setEndTime(Instant.now());
        driver.setOnShift(false);
        driver.setCurrentLocation(null);

        // Persist
        shiftRepository.save(activeShift);
        driverRepository.save(driver);
        
        // Trigger optimization in case this driver had pending assignments
        try {
            log.info("Driver {} ended shift, triggering optimization for reassignment", driverId);
            assignmentService.optimizeAndAssign(OptimizationTrigger.MANUAL, driverId);
        } catch (Exception e) {
            log.error("Optimization failed after shift end", e);
            // Don't fail shift end if optimization fails
        }

        return new DriverEndShiftDto(activeShift.getId(),driver.getId(),activeShift.getEndTime());
    }
}
