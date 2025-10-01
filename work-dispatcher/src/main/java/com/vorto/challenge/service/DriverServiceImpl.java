package com.vorto.challenge.service;

import com.vorto.challenge.DTO.*;
import com.vorto.challenge.DriverMapper;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.model.Shift;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.LoadRepository;
import com.vorto.challenge.repository.ShiftRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class DriverServiceImpl implements DriverService{
    private final DriverRepository driverRepository;
    private final ShiftRepository shiftRepository;     // <-- inject
    private final LoadRepository loadRepository;

    public DriverServiceImpl(DriverRepository driverRepository, ShiftRepository shiftRepository, LoadRepository loadRepository) {
        this.driverRepository = driverRepository;
        this.shiftRepository = shiftRepository;
        this.loadRepository = loadRepository;
    }
    @Override
    @Transactional
    public LoginOutcome loginOrCreate(LoginRequest request) {
        String normalized = normalize(request.username());
        // If exists → return it with created=false
        Optional<Driver> existing = driverRepository.findByNameIgnoreCase(normalized);
        if (existing.isPresent()) {
            return new LoginOutcome(false, DriverMapper.toDto(existing.get()));
        }

        // Else create → return created entity with created=true
        Driver saved = driverRepository.save(newDriver(normalized));
        return new LoginOutcome(true, DriverMapper.toDto(saved));
    }


    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<DriverDto> get(UUID id) {
        return driverRepository.findById(id).map(DriverMapper::toDto);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public DriverStateResponse getDriverState(UUID driverId) {
        Driver d = driverRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + driverId));

        // Active shift (derived from DB, not just the boolean)
        Optional<Shift> optShift = shiftRepository.findActiveShift(driverId);
        boolean onShift = optShift.isPresent();

        // DriverDto (lat/lng from currentLocation geometry)
        Double lat = null, lng = null;
        Point cur = d.getCurrentLocation();
        if (cur != null) {
            // JTS: X=longitude, Y=latitude
            lat = cur.getY();
            lng = cur.getX();
        }
        DriverDto driverDto = new DriverDto(d.getId(), d.getName(), onShift, lat, lng);

        // ShiftDto (if on shift)
        ShiftDto shiftDto = optShift
                .map(s -> {
                    Double sLat = null, sLng = null;
                    Point p = s.getStartLocation();
                    if (p != null) {
                        sLat = p.getY();
                        sLng = p.getX();
                    }
                    return new ShiftDto(s.getId(), s.getStartTime(), sLat, sLng);
                })
                .orElse(null);

        // Active load (RESERVED / IN_PROGRESS) → LoadSummaryDto or null
        LoadSummaryDto loadDto = null;
        if (onShift) {
            var activeStatuses = EnumSet.of(Load.Status.RESERVED, Load.Status.IN_PROGRESS);
            Optional<Load> optLoad = loadRepository.findOpenByDriverId(driverId, activeStatuses);
            loadDto = optLoad.map(this::toLoadSummary).orElse(null);
        }

        return new DriverStateResponse(driverDto, shiftDto, loadDto);
    }

    // ---- helpers ---------------------------------------------------
    private String normalize(String u) {
        String t = (u == null) ? "" : u.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("username is required");
        return t.toLowerCase(Locale.ROOT);
    }

    private LoadSummaryDto toLoadSummary(Load l) {
        // pickup/dropoff geometry → DTO LatLng
        LoadSummaryDto.LatLng pickup = toLatLng(l.getPickup());
        LoadSummaryDto.LatLng dropoff = toLatLng(l.getDropoff());

        // currentStop/status as Strings for your UI
        String status = l.getStatus().name();           // AWAITING_DRIVER / RESERVED / IN_PROGRESS / COMPLETED
        String currentStop = (l.getCurrentStop() != null) ? l.getCurrentStop().name() : null; // PICKUP / DROPOFF

        // assigned driver (lite)
        LoadSummaryDto.DriverLite assigned = null;
        if (l.getAssignedDriver() != null) {
            assigned = new LoadSummaryDto.DriverLite(
                    l.getAssignedDriver().getId(),
                    l.getAssignedDriver().getName()
            );
        }

        return new LoadSummaryDto(
                l.getId(),
                status,
                currentStop,
                pickup,
                dropoff,
                assigned
        );
    }

    private LoadSummaryDto.LatLng toLatLng(Point p) {
        if (p == null) return null;
        // JTS: (x,y)=(lon,lat)
        return new LoadSummaryDto.LatLng(p.getY(), p.getX());
    }
    private Driver newDriver(String name) {
        Driver d = new Driver();
        d.setName(name);
        d.setOnShift(false);
        return d;
    }
}
