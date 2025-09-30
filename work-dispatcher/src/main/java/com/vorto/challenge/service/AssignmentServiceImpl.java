package com.vorto.challenge.service;

import com.vorto.challenge.DTO.CompleteStopResult;
import com.vorto.challenge.DTO.LoadAssignmentResponse;
import com.vorto.challenge.DTO.RejectOutcome;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.model.Shift;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.LoadRepository;
import com.vorto.challenge.repository.ShiftRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AssignmentServiceImpl implements AssignmentService{
    private static final int RESERVATION_SECONDS = 120;

    private final DriverRepository driverRepo;
    private final ShiftRepository shiftRepo;
    private final LoadRepository loadRepo;

    public AssignmentServiceImpl(DriverRepository driverRepo, ShiftRepository shiftRepo, LoadRepository loadRepo) {
        this.driverRepo = driverRepo;
        this.shiftRepo = shiftRepo;
        this.loadRepo = loadRepo;
    }

    @Override
    @Transactional
    public LoadAssignmentResponse getOrReserveLoad(UUID driverId){
        Driver driver = driverRepo.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + driverId));

        // must be on an active shift
        Shift activeShift = shiftRepo.findActiveShift(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Driver is off-shift"));

        // release any expired reservations before selecting
        loadRepo.releaseExpiredReservations(Instant.now());

        // already has RESERVED/IN_PROGRESS?
        Load open = loadRepo.findOpenByDriverId(
                driverId,
                List.of(Load.Status.RESERVED, Load.Status.IN_PROGRESS)
        ).orElse(null);
        if (open != null) return toDto(open);

        if (driver.getCurrentLocation() == null) {
            throw new IllegalStateException("Driver location unknown; cannot assign");
        }
        // Reserve the closest available from driver's current location
        return reserveClosestFrom(driver, activeShift, null);

    }


    @Override
    @Transactional
    public CompleteStopResult completeNextStop(UUID driverId, UUID loadId) {
        // driver + active shift required
        Driver driver = driverRepo.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + driverId));
        Shift activeShift = shiftRepo.findActiveShift(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Driver is off-shift"));

        Load l = loadRepo.findById(loadId)
                .orElseThrow(() -> new EntityNotFoundException("Load not found: " + loadId));

        // Idempotency: if already completed, return completed + any current open (or try to reserve one now)
        if (l.getStatus() == Load.Status.COMPLETED) {
            Load open = loadRepo.findOpenByDriverId(
                    driverId, List.of(Load.Status.RESERVED, Load.Status.IN_PROGRESS)
            ).orElse(null);

            LoadAssignmentResponse next =
                    (open != null) ? toDto(open)
                            : (driver.getCurrentLocation() != null
                            ? reserveClosestFrom(driver, activeShift, l.getId())
                            : null);

            return new CompleteStopResult(
                    toDto(l),
                    next
            );
        }

        // Ownership for non-completed loads
        if (l.getAssignedDriver() == null || !driverId.equals(l.getAssignedDriver().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Load not assigned to this driver");
        }

        // STATE MACHINE
        // RESERVED + PICKUP -> IN_PROGRESS + DROPOFF
        if (l.getStatus() == Load.Status.RESERVED && l.getCurrentStop() == Load.StopKind.PICKUP) {
            // pickup step: ensure reservation not expired
            if (l.getReservationExpiresAt() != null && l.getReservationExpiresAt().isBefore(Instant.now())) {
                // release and ask client to fetch again
                releaseReservation(l);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation expired. Fetch assignment again.");
            }
            l.setStatus(Load.Status.IN_PROGRESS);
            l.setCurrentStop(Load.StopKind.DROPOFF);
            l.setReservationExpiresAt(null);

            // snap driver location to pickup
            driver.setCurrentLocation(l.getPickup());
            driverRepo.save(driver);
            loadRepo.save(l);

            return new CompleteStopResult(
                    toDto(l),   // same load, now IN_PROGRESS
                    null        // we don't search for a new one at pickup
            );
        }

        // IN_PROGRESS + DROPOFF -> COMPLETED and auto-assign next
        if (l.getStatus() == Load.Status.IN_PROGRESS && l.getCurrentStop() == Load.StopKind.DROPOFF) {
            l.setStatus(Load.Status.COMPLETED);
            l.setReservationExpiresAt(null);

            // snap driver to dropoff; clear assignment so they’re idle but on-shift
            driver.setCurrentLocation(l.getDropoff());
            l.setAssignedDriver(null);
            l.setAssignedShift(null);

            driverRepo.save(driver);
            loadRepo.save(l);

            // Immediately try to reserve the next closest based on new location
            LoadAssignmentResponse next = reserveClosestFrom(driver, activeShift, l.getId());

            return new CompleteStopResult(
                    toDto(l),  // completed
                    next       // may be null if none available
            );
        }

        throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid state for completing next stop");
    }
    @Override
    @Transactional
    public RejectOutcome rejectReservedLoadAndEndShift(UUID driverId, UUID loadId) {
        // Load must exist and be RESERVED by this driver
        Load l = loadRepo.findById(loadId)
                .orElseThrow(() -> new EntityNotFoundException("Load not found: " + loadId));
        boolean alreadyReleased = l.getStatus() != Load.Status.RESERVED
                || l.getAssignedDriver() == null
                || !driverId.equals(l.getAssignedDriver().getId());

        // also check driver is already off-shift
        boolean offShift = shiftRepo.findActiveShift(driverId).isEmpty();

        if (alreadyReleased && offShift) {
            return new RejectOutcome(driverId, null, loadId,
                    "NO_OP_ALREADY_REJECTED_AND_SHIFT_ENDED", Instant.now());
        }

        if (l.getAssignedDriver() == null || !driverId.equals(l.getAssignedDriver().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Load not reserved by this driver");
        }
        if (l.getStatus() != Load.Status.RESERVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only RESERVED loads can be rejected");
        }

        // Release the reservation back to the pool
        releaseReservation(l);

        // End the active shift (required by your rule)
        Shift activeShift = shiftRepo.findActiveShift(driverId)
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


    // ===================== Helpers =====================
    /**
     * Reserve the closest AWAITING_DRIVER load for the given on-shift driver, using their current location.
     * - Cleans up expired reservations first
     * - Excludes the given loadId (e.g., the one just completed)
     * - Returns a DTO for the reserved load, or null if none available
     */
    private LoadAssignmentResponse reserveClosestFrom(Driver driver, Shift activeShift, UUID excludeId) {
        // Clean up pool first
        loadRepo.releaseExpiredReservations(Instant.now());

        if (driver.getCurrentLocation() == null) {
            return null; // cannot place the driver on the map
        }

        final double lat = driver.getCurrentLocation().getY(); // lat
        final double lng = driver.getCurrentLocation().getX(); // lon

        Load candidate = loadRepo.pickClosestAvailableForReservation(lat, lng, excludeId).orElse(null);
        if (candidate == null) return null;

        candidate.setAssignedDriver(driver);
        candidate.setAssignedShift(activeShift);
        candidate.setStatus(Load.Status.RESERVED);
        candidate.setReservationExpiresAt(Instant.now().plus(RESERVATION_SECONDS, ChronoUnit.SECONDS));
        loadRepo.save(candidate);

        return toDto(candidate);
    }
    private LoadAssignmentResponse toDto(Load l) {
        return new LoadAssignmentResponse(
                l.getId().toString(),
                l.getPickup().getY(),  // lat
                l.getPickup().getX(),  // lon
                l.getDropoff().getY(),
                l.getDropoff().getX(),
                l.getStatus().name(),
                l.getCurrentStop().name()
        );
    }
    private void releaseReservation(Load l) {
        l.setStatus(Load.Status.AWAITING_DRIVER);
        l.setAssignedDriver(null);
        l.setAssignedShift(null);
        l.setReservationExpiresAt(null);
        loadRepo.save(l);
    }
}
