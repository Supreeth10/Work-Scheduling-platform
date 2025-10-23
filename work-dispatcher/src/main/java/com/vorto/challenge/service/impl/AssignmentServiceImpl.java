package com.vorto.challenge.service.impl;

import com.vorto.challenge.DTO.CompleteStopResult;
import com.vorto.challenge.DTO.LoadAssignmentResponse;
import com.vorto.challenge.DTO.RejectOutcome;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.model.Shift;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.LoadRepository;
import com.vorto.challenge.repository.ShiftRepository;
import com.vorto.challenge.service.AssignmentService;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.vorto.challenge.common.LoadMappers.toAssignmentResponse;

@Service
public class AssignmentServiceImpl implements AssignmentService {
    private static final int RESERVATION_SECONDS = 120;

    private final DriverRepository driverRepo;
    private final ShiftRepository shiftRepo;
    private final LoadRepository loadRepo;

    public AssignmentServiceImpl(DriverRepository driverRepo, ShiftRepository shiftRepo, LoadRepository loadRepo) {
        this.driverRepo = driverRepo;
        this.shiftRepo = shiftRepo;
        this.loadRepo = loadRepo;
    }

    /**
     * Returns the driver's current open assignment if one exists; otherwise reserves
     * the nearest available load based on the driver's current location.
     * Requires the driver to be on an active shift. Idempotent fetch if already assigned.
     */
    @Override
    @Transactional
    public LoadAssignmentResponse getOrReserveLoad(UUID driverId){
        //check if driver exists
        Driver driver = driverRepo.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + driverId));

        // driver must be on an active shift
        Shift activeShift = shiftRepo.findByDriverIdAndEndTimeIsNull(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Driver is off-shift"));

        // release any expired reservations before selecting
        loadRepo.releaseExpiredReservations(Instant.now());

        // check if driver already has RESERVED/IN_PROGRESS loads. Idempotent check
        Load openLoad = loadRepo.findOpenByDriverId(
                driverId,
                List.of(Load.Status.RESERVED, Load.Status.IN_PROGRESS)
        ).orElse(null);
        // If driver already has an open load return it (idempotent fetch).
        if (openLoad != null) return toAssignmentResponse(openLoad);

        if (driver.getCurrentLocation() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver location unknown");
        }
        // Reserve the closest available load from driver's current location
        return reserveClosestFrom(driver, activeShift, null);

    }


    /**
     * Advances the load state machine (PICKUP -> DROPOFF -> COMPLETE) for this driver.
     * Enforces ownership, snaps driver location at each stop, and on completion
     * attempts to reserve the next closest load. Idempotent if the load is already completed.
     */
    @Override
    @Transactional
    public CompleteStopResult completeNextStop(UUID driverId, UUID loadId) {
        // driver must exist and be on an active shift
        Driver driver = driverRepo.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + driverId));
        Shift activeShift = shiftRepo.findByDriverIdAndEndTimeIsNull(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Driver is off-shift"));
        // Load must exist
        Load load = loadRepo.findById(loadId)
                .orElseThrow(() -> new EntityNotFoundException("Load not found: " + loadId));

        /* Idempotency: if load already completed, return completed load + driver's next assignment (if any)
            or try to reserve one now (based on driver's current location)*/
        if (load.getStatus() == Load.Status.COMPLETED) {
            Load openLoad = loadRepo.findOpenByDriverId(
                    driverId, List.of(Load.Status.RESERVED, Load.Status.IN_PROGRESS)
            ).orElse(null);

            LoadAssignmentResponse nextLoadAssignment =
                    (openLoad != null) ? toAssignmentResponse(openLoad)
                            : (driver.getCurrentLocation() != null
                            ? reserveClosestFrom(driver, activeShift, load.getId())
                            : null);

            return new CompleteStopResult(
                    toAssignmentResponse(load),
                    nextLoadAssignment
            );
        }

        // Ownership check for non-completed loads: requestor must be the assigned driver
        if (load.getAssignedDriver() == null || !driverId.equals(load.getAssignedDriver().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Load not assigned to this driver");
        }

        // STATE MACHINE
        // RESERVED + PICKUP -> IN_PROGRESS + DROPOFF
        if (load.getStatus() == Load.Status.RESERVED && load.getCurrentStop() == Load.StopKind.PICKUP) {
            // pickup step: ensure reservation not expired
            if (load.getReservationExpiresAt() != null && load.getReservationExpiresAt().isBefore(Instant.now())) {
                // release and ask client to fetch again
                releaseReservation(load);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation expired. Fetch assignment again.");
            }
            load.setStatus(Load.Status.IN_PROGRESS);
            load.setCurrentStop(Load.StopKind.DROPOFF);
            load.setReservationExpiresAt(null);

            // snap driver location to pickup
            driver.setCurrentLocation(load.getPickup());
            driverRepo.save(driver);
            loadRepo.save(load);

            return new CompleteStopResult(
                    toAssignmentResponse(load),   // same load, now IN_PROGRESS
                    null        // we don't search for a new load at pickup
            );
        }

        // IN_PROGRESS + DROPOFF -> COMPLETED and auto-assign next
        if (load.getStatus() == Load.Status.IN_PROGRESS && load.getCurrentStop() == Load.StopKind.DROPOFF) {
            load.setStatus(Load.Status.COMPLETED);
            load.setReservationExpiresAt(null);

            // snap driver to dropoff; clear assignment, so they’re idle but on-shift
            driver.setCurrentLocation(load.getDropoff());
            load.setAssignedDriver(null);
            load.setAssignedShift(null);

            driverRepo.save(driver);
            loadRepo.save(load);

            // Immediately try to reserve the next closest based on new location
            LoadAssignmentResponse nextLoadAssignment = reserveClosestFrom(driver, activeShift, load.getId());

            return new CompleteStopResult(
                    toAssignmentResponse(load),  // completed load
                    nextLoadAssignment       // next assignment (maybe null)
            );
        }
        //Any other combination of status/stop is invalid for "complete next stop"
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid state for completing next stop");
    }

    /**
     * Releases a RESERVED load held by this driver back to the pool and ends the driver's shift.
     * Enforces ownership and state (must be RESERVED). Idempotent fast-path if already released
     * and the driver is already off shift.
     */
    @Override
    @Transactional
    public RejectOutcome rejectReservedLoadAndEndShift(UUID driverId, UUID loadId) {
        // Load must exist and be RESERVED by this driver
        Load load = loadRepo.findById(loadId)
                .orElseThrow(() -> new EntityNotFoundException("Load not found: " + loadId));

        // Ownership required for reject
        if (load.getAssignedDriver() == null || !driverId.equals(load.getAssignedDriver().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Load not assigned to this driver");
        }

        //check if load has already been released or doesn't belong to driver
        boolean alreadyReleased =  load.getStatus() != Load.Status.RESERVED
                                || load.getAssignedDriver() == null
                                || !driverId.equals(load.getAssignedDriver().getId());

        // also check driver is already off-shift
        boolean offShift = shiftRepo.findByDriverIdAndEndTimeIsNull(driverId).isEmpty();

        // Idempotency/NO-OP: if the reservation is already released AND the driver is already off shift
        if (alreadyReleased && offShift) {
            return new RejectOutcome(driverId, null, loadId,
                    "NO_OP_ALREADY_REJECTED_AND_SHIFT_ENDED", Instant.now());
        }


        if (load.getStatus() != Load.Status.RESERVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only reserved loads can be rejected");
        }

        // Release the reservation back to the pool
        releaseReservation(load);

        // End the active shift
        Shift activeShift = shiftRepo.findByDriverIdAndEndTimeIsNull(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Driver is off-shift"));

        Instant endedAt = Instant.now();
        activeShift.setEndTime(endedAt);

        Driver driver = activeShift.getDriver();
        driver.setOnShift(false);
        driver.setCurrentLocation(null);
        shiftRepo.save(activeShift);
        driverRepo.save(driver);

        return new RejectOutcome(
                driverId,
                activeShift.getId(),
                loadId,
                "REJECTED_AND_SHIFT_ENDED",
                endedAt
        );
    }

    /**
     * After a load is created: tries to immediately reserve it for the closest eligible
     * on-shift driver with no open load. No-op if none found or state changed concurrently.
     */
    @Override
    @Transactional
    public void tryAssignNewlyCreatedLoad(UUID loadId) {
        // Load must exist and still be unassigned & awaiting driver
        Load load = loadRepo.findById(loadId)
                .orElseThrow(() -> new EntityNotFoundException("Load not found: " + loadId));

        if (load.getStatus() != Load.Status.AWAITING_DRIVER || load.getPickup() == null) {
            return; // nothing to do (might have been reserved by some other flow)
        }

        // Clean pool first (consistent with other flows)
        loadRepo.releaseExpiredReservations(Instant.now());

        // Find the closest on-shift driver with no open (RESERVED/IN_PROGRESS) load
        double lat = load.getPickup().getY();
        double lng = load.getPickup().getX();

        Driver driver = driverRepo.findClosestAvailableDriver(lat, lng).orElse(null);
        if (driver == null) return;

        // Need the active shift to attach the reservation
        Shift activeShift = shiftRepo.findByDriverIdAndEndTimeIsNull(driver.getId())
                .orElse(null);
        if (activeShift == null) return; // race: driver went off-shift

        // Reserve this specific load for the chosen driver
        try {
            int updated = loadRepo.reserveById(loadId, driver.getId(), activeShift.getId(), RESERVATION_SECONDS);
            // If updated == 0, someone else reserved or state changed; no-op.
        } catch (DataIntegrityViolationException ignored) {
            // Another request gave this driver an open load concurrently—ignore.
        }
    }


    // ===================== Helpers =====================
    /**
     * Internal: reserves the nearest AWAITING_DRIVER load for the given on-shift driver,
     * excluding a specific load ID (e.g., the one just completed). Returns the assignment
     * DTO if reserved, or null if none available.
     */
    private LoadAssignmentResponse reserveClosestFrom(Driver driver, Shift activeShift, UUID excludeId) {
        loadRepo.releaseExpiredReservations(Instant.now());

        if (driver.getCurrentLocation() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver location unknown; cannot assign");
        }

        final double lat = driver.getCurrentLocation().getY();
        final double lng = driver.getCurrentLocation().getX();

        // 1) Lock the nearest candidate (respect excludeId)
        UUID candId = loadRepo.lockClosestAvailableId(lat, lng, excludeId).orElse(null);
        if (candId == null) return null;

        // 2) Reserve it atomically
        try {
            int updated = loadRepo.reserveById(candId, driver.getId(), activeShift.getId(), RESERVATION_SECONDS);
            if (updated == 0) {
                // Lost a race in the tiny window — just report no assignment
                return null;
            }
        } catch (DataIntegrityViolationException | ConstraintViolationException e) {
            // Unique index "one open per driver" may have tripped; return the already-open load if present
            Load stillOpen = loadRepo.findOpenByDriverId(
                    driver.getId(), List.of(Load.Status.RESERVED, Load.Status.IN_PROGRESS)
            ).orElse(null);
            if (stillOpen != null) return toAssignmentResponse(stillOpen);
            throw e;
        }

        // 3) Load and return DTO
        Load reserved = loadRepo.findById(candId).orElseThrow();
        return toAssignmentResponse(reserved);
    }


    /**
     * Internal: returns the load to AWAITING_DRIVER by clearing assignment and reservation metadata.
     */
    private void releaseReservation(Load l) {
        l.setStatus(Load.Status.AWAITING_DRIVER);
        l.setAssignedDriver(null);
        l.setAssignedShift(null);
        l.setReservationExpiresAt(null);
        loadRepo.save(l);
    }
}
