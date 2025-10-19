package com.vorto.challenge.service.impl;

import com.vorto.challenge.DTO.*;
import com.vorto.challenge.common.DriverMapper;
import com.vorto.challenge.common.LoadMappers;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.model.Shift;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.LoadRepository;
import com.vorto.challenge.repository.ShiftRepository;
import com.vorto.challenge.service.DriverService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;


import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static com.vorto.challenge.common.JtsGeo.toLatLng;

import static com.vorto.challenge.common.TextNormalizer.normalizeUsername;

@Service
public class DriverServiceImpl implements DriverService {
    private final DriverRepository driverRepository;
    private final ShiftRepository shiftRepository;
    private final LoadRepository loadRepository;

    public DriverServiceImpl(DriverRepository driverRepository, ShiftRepository shiftRepository, LoadRepository loadRepository) {
        this.driverRepository = driverRepository;
        this.shiftRepository = shiftRepository;
        this.loadRepository = loadRepository;
    }
    @Override
    @Transactional
    public LoginOutcome loginOrCreate(LoginRequest request) {
        String normalized = normalizeUsername(request.username());

        // If exists → return it with created=false
        Optional<Driver> existing = driverRepository.findByNameIgnoreCase(normalized);
        if (existing.isPresent()) {
            return new LoginOutcome(false, DriverMapper.toDto(existing.get()));
        }

        // Else create → return created entity with created=true
        Driver saved = driverRepository.save(newDriver(normalized));
        return new LoginOutcome(true, DriverMapper.toDto(saved));
    }


    @Transactional(readOnly = true)
    public Optional<DriverDto> get(UUID id) {
        return Optional.ofNullable(driverRepository.findById(id).map(DriverMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public DriverStateResponse getDriverState(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + driverId));

        // Active shift (derived from DB, not just the boolean)
        Optional<Shift> optShift = shiftRepository.findByDriverIdAndEndTimeIsNull(driverId);
        boolean onShift = optShift.isPresent();

        // DriverDto (lat/lng from currentLocation geometry)
        LocationDto curLoc = toLatLng(driver.getCurrentLocation());
        Double dLat = (curLoc == null) ? null : curLoc.lat();
        Double dLng = (curLoc == null) ? null : curLoc.lng();
        DriverDto driverDto = new DriverDto(driver.getId(), driver.getName(), onShift, dLat, dLng);

       // ShiftDto (if on shift)
        ShiftDto shiftDto = optShift
                .map(s -> {
                    LocationDto startLoc = toLatLng(s.getStartLocation());
                    Double sLat = (startLoc == null) ? null : startLoc.lat();
                    Double sLng = (startLoc == null) ? null : startLoc.lng();
                    return new ShiftDto(s.getId(), s.getStartTime(), sLat, sLng);
                })
                .orElse(null);


        // Active load (RESERVED / IN_PROGRESS) → LoadSummaryDto or null
        LoadSummaryDto loadDto = null;
        if (onShift) {
            var activeStatuses = EnumSet.of(Load.Status.RESERVED, Load.Status.IN_PROGRESS);
            loadDto = loadRepository.findOpenByDriverId(driverId, activeStatuses)
                    .map(LoadMappers::toLoadSummaryDto)
                    .orElse(null);
        }
        return new DriverStateResponse(driverDto, shiftDto, loadDto);
    }

    // ---- helpers ---------------------------------------------------
    private Driver newDriver(String name) {
        Driver d = new Driver();
        d.setName(name);
        d.setOnShift(false);
        return d;
    }
}
