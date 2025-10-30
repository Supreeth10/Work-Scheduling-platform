package com.vorto.challenge.service.impl;

import com.vorto.challenge.DTO.CompleteStopResult;
import com.vorto.challenge.DTO.LoadAssignmentResponse;
import com.vorto.challenge.DTO.RejectOutcome;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.model.Shift;
import com.vorto.challenge.optimization.*;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.LoadRepository;
import com.vorto.challenge.repository.ShiftRepository;
import com.vorto.challenge.service.AssignmentService;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.vorto.challenge.common.LoadMappers.toAssignmentResponse;

@Service
public class AssignmentServiceImpl implements AssignmentService {
    private static final Logger log = LoggerFactory.getLogger(AssignmentServiceImpl.class);
    private static final int RESERVATION_SECONDS = 120;

    private final DriverRepository driverRepo;
    private final ShiftRepository shiftRepo;
    private final LoadRepository loadRepo;
    private final OptimizationService optimizationService;

    public AssignmentServiceImpl(
            DriverRepository driverRepo, 
            ShiftRepository shiftRepo, 
            LoadRepository loadRepo,
            OptimizationService optimizationService) {
        this.driverRepo = driverRepo;
        this.shiftRepo = shiftRepo;
        this.loadRepo = loadRepo;
        this.optimizationService = optimizationService;
    }
    
    /**
     * NEW UNIFIED ENTRY POINT: Optimize and apply load assignments.
     * This is the single algorithm that handles all assignment logic.
     * Replaces duplicated logic in getOrReserveLoad, tryAssignNewlyCreatedLoad, etc.
     * 
     * @param trigger The event that triggered this optimization
     * @param triggeringEntityId ID of the entity (driver or load) that triggered optimization
     * @return AssignmentPlan with the optimal assignments
     */
    @Transactional
    public AssignmentPlan optimizeAndAssign(OptimizationTrigger trigger, UUID triggeringEntityId) {
        log.info("Optimizing assignments: trigger={}, entityId={}", trigger, triggeringEntityId);
        
        // 1. Release expired reservations first
        loadRepo.releaseExpiredReservations(Instant.now());
        
        // 2. Gather current system state
        List<Driver> eligibleDrivers = driverRepo.findAllEligibleForAssignment();
        List<Load> assignableLoads = loadRepo.findAllAssignable();
        List<Load> protectedLoads = loadRepo.findAllInProgress();
        
        log.debug("System state: {} eligible drivers, {} assignable loads, {} protected loads",
                eligibleDrivers.size(), assignableLoads.size(), protectedLoads.size());
        
        // 3. Build optimization context
        OptimizationContext context = new OptimizationContext(
                eligibleDrivers,
                assignableLoads,
                protectedLoads,
                trigger,
                triggeringEntityId
        );
        
        // 4. Run optimization
        AssignmentPlan plan = optimizationService.optimize(context);
        
        // 5. Apply the plan to the database
        applyAssignmentPlan(plan, assignableLoads);
        
        log.info("Optimization complete: {} assignments made, {:.2f} mi total deadhead",
                plan.getAssignedDriverCount(), plan.getTotalDeadheadMiles());
        
        return plan;
    }
    
    /**
     * Apply the optimization plan to the database by creating/updating/releasing reservations.
     * 
     * Note on chaining: When MIP suggests a chain (D1 → L1 → L2), we only reserve L1.
     * L2 will be assigned when D1 completes L1 dropoff via re-optimization.
     * This is a "soft preference" approach - allows flexibility if better options appear.
     */
    private void applyAssignmentPlan(AssignmentPlan plan, List<Load> assignableLoads) {
        // Build map of current assignments for comparison
        Map<UUID, UUID> currentAssignments = new HashMap<>();
        for (Load load : assignableLoads) {
            if (load.getStatus() == Load.Status.RESERVED && load.getAssignedDriver() != null) {
                currentAssignments.put(load.getId(), load.getAssignedDriver().getId());
            }
        }
        
        Set<UUID> assignedLoadIds = plan.getAssignedLoadIds();
        
        // Release loads that were RESERVED but not in new plan (aggressive rebalancing)
        for (Load load : assignableLoads) {
            if (load.getStatus() == Load.Status.RESERVED && !assignedLoadIds.contains(load.getId())) {
                log.debug("Releasing load {} (was reserved to {}) for rebalancing", 
                        load.getId(), load.getAssignedDriver().getId());
                load.setStatus(Load.Status.AWAITING_DRIVER);
                load.setAssignedDriver(null);
                load.setAssignedShift(null);
                load.setReservationExpiresAt(null);
                loadRepo.save(load);
            }
        }
        
        // Apply new assignments
        for (Map.Entry<UUID, LoadSequence> entry : plan.getDriverAssignments().entrySet()) {
            UUID driverId = entry.getKey();
            LoadSequence sequence = entry.getValue();
            
            if (sequence.isEmpty()) continue; // Skip idle drivers
            
            Driver driver = driverRepo.findById(driverId)
                    .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + driverId));
            
            Shift activeShift = shiftRepo.findByDriverIdAndEndTimeIsNull(driverId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, 
                            "Driver has no active shift: " + driverId));
            
            // For chained sequences: only reserve FIRST load
            // Second load will be assigned after first dropoff via re-optimization
            UUID firstLoadId = sequence.getLoadIds().get(0);
            
            // Check if already assigned correctly (skip unnecessary updates)
            UUID currentDriverId = currentAssignments.get(firstLoadId);
            if (driverId.equals(currentDriverId)) {
                log.debug("Load {} already assigned to driver {}, no change", firstLoadId, driverId);
                continue;
            }
            
            Load load = loadRepo.findById(firstLoadId)
                    .orElseThrow(() -> new EntityNotFoundException("Load not found: " + firstLoadId));
            
            // Reserve the load
            log.debug("Assigning load {} to driver {} (deadhead: {:.2f} mi)", 
                    firstLoadId, driverId, sequence.getDeadheadMiles());
            
            load.setStatus(Load.Status.RESERVED);
            load.setAssignedDriver(driver);
            load.setAssignedShift(activeShift);
            load.setReservationExpiresAt(Instant.now().plus(RESERVATION_SECONDS, ChronoUnit.SECONDS));
            loadRepo.save(load);
            
            // Note: If chained (sequence.size() == 2), second load handled after first dropoff
            if (sequence.isChained()) {
                log.debug("Chain suggested: {} loads for driver {} (second load assigned after first completes)",
                        sequence.size(), driverId);
            }
        }
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
        
        // Trigger optimization to assign a load to this driver
        try {
            AssignmentPlan plan = optimizeAndAssign(OptimizationTrigger.MANUAL, driverId);
            LoadSequence driverSeq = plan.getAssignmentForDriver(driverId);
            if (driverSeq != null && !driverSeq.isEmpty()) {
                UUID assignedLoadId = driverSeq.getLoadIds().get(0);
                Load assignedLoad = loadRepo.findById(assignedLoadId).orElse(null);
                if (assignedLoad != null) {
                    return toAssignmentResponse(assignedLoad);
                }
            }
        } catch (Exception e) {
            log.error("Optimization failed for getOrReserveLoad", e);
        }
        
        // No load available
        return null;
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

        /* Idempotency: if load already completed, return completed load + driver's next assignment (if any) */
        if (load.getStatus() == Load.Status.COMPLETED) {
            Load openLoad = loadRepo.findOpenByDriverId(
                    driverId, List.of(Load.Status.RESERVED, Load.Status.IN_PROGRESS)
            ).orElse(null);

            LoadAssignmentResponse nextLoadAssignment = null;
            if (openLoad != null) {
                nextLoadAssignment = toAssignmentResponse(openLoad);
            } else if (driver.getCurrentLocation() != null) {
                // Try optimization to get next assignment
                try {
                    AssignmentPlan plan = optimizeAndAssign(OptimizationTrigger.MANUAL, driverId);
                    LoadSequence driverSeq = plan.getAssignmentForDriver(driverId);
                    if (driverSeq != null && !driverSeq.isEmpty()) {
                        UUID nextLoadId = driverSeq.getLoadIds().get(0);
                        Load nextLoad = loadRepo.findById(nextLoadId).orElse(null);
                        if (nextLoad != null) {
                            nextLoadAssignment = toAssignmentResponse(nextLoad);
                        }
                    }
                } catch (Exception e) {
                    log.error("Optimization failed in idempotency check", e);
                }
            }

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

            // snap driver to dropoff; clear assignment, so they're idle but on-shift
            driver.setCurrentLocation(load.getDropoff());
            load.setAssignedDriver(null);
            load.setAssignedShift(null);

            driverRepo.save(driver);
            loadRepo.save(load);

            // Trigger optimization to assign next load
            // Note: If this was part of a chain, MIP will likely assign the second load
            LoadAssignmentResponse nextLoadAssignment = null;
            try {
                log.info("Driver {} completed dropoff for load {}, triggering optimization", driverId, loadId);
                AssignmentPlan plan = optimizeAndAssign(OptimizationTrigger.DROPOFF_COMPLETE, driverId);
                
                LoadSequence driverSeq = plan.getAssignmentForDriver(driverId);
                if (driverSeq != null && !driverSeq.isEmpty()) {
                    UUID nextLoadId = driverSeq.getLoadIds().get(0);
                    Load nextLoad = loadRepo.findById(nextLoadId).orElse(null);
                    if (nextLoad != null) {
                        nextLoadAssignment = toAssignmentResponse(nextLoad);
                    }
                }
            } catch (Exception e) {
                log.error("Optimization failed after dropoff complete", e);
            }

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
     * @deprecated Replaced by optimizeAndAssign() which uses MIP optimization.
     * Kept for backward compatibility, delegates to new system.
     */
    @Deprecated
    @Override
    @Transactional
    public void tryAssignNewlyCreatedLoad(UUID loadId) {
        log.warn("tryAssignNewlyCreatedLoad() is deprecated. Delegating to optimizeAndAssign().");
        optimizeAndAssign(OptimizationTrigger.LOAD_CREATED, loadId);
    }


    // ===================== Helpers =====================
    /**
     * @deprecated Replaced by optimizeAndAssign() which uses MIP optimization.
     * No longer used - all assignment logic now goes through unified entry point.
     * Kept temporarily for reference, will be removed in cleanup phase.
     */
    @Deprecated
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
