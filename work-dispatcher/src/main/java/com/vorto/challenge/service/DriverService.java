package com.vorto.challenge.service;


import aj.org.objectweb.asm.commons.Remapper;
import com.vorto.challenge.DTO.DriverDto;
import com.vorto.challenge.DTO.LoginRequest;
import com.vorto.challenge.model.Driver;

import java.util.Optional;
import java.util.UUID;

public interface DriverService {
    /**
     * Logs in or creates the driver.
     * @return If created, the new Driver entity; if already existed, return null.
     */
    Driver loginOrCreate(LoginRequest request);
    Optional<DriverDto> get(UUID id);
}
