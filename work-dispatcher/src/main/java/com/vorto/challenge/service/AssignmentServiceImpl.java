package com.vorto.challenge.service;

import com.vorto.challenge.DTO.LoadAssignmentResponse;
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
    private static final int RESERVATION_SECONDS = 60;

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

        double lat = driver.getCurrentLocation().getY(); // lat
        double lng = driver.getCurrentLocation().getX(); // lon

        Load candidate = loadRepo.pickClosestAvailableForReservation(lat, lng, null).orElse(null);
        if (candidate == null) return null; // 204

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
}
