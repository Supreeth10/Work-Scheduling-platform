package com.vorto.challenge.service;

import com.vorto.challenge.DTO.DriverDto;
import com.vorto.challenge.DTO.LoginRequest;
import com.vorto.challenge.DriverMapper;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.repository.DriverRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class DriverServiceImpl implements DriverService{
    private final DriverRepository driverRepository;

    public DriverServiceImpl(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }
    @Override
    @Transactional
    public Driver loginOrCreate(LoginRequest request) {
        String normalized = normalize(request.username());
        // If exists → controller will return 200 (no body)
        if (driverRepository.existsByNameIgnoreCase(normalized)) {
            return null;
        }

        Driver driver = new Driver();
        driver.setName(normalized);
        driver.setOnShift(false);
        return driverRepository.save(driver);
    }
    private String normalize(String u) {
        String t = (u == null) ? "" : u.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("username is required");
        return t.toLowerCase(Locale.ROOT);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<DriverDto> get(UUID id) {
        return driverRepository.findById(id).map(DriverMapper::toDto);
    }


}
