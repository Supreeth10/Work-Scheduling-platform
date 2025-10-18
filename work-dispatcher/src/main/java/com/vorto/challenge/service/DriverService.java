package com.vorto.challenge.service;



import com.vorto.challenge.DTO.DriverDto;
import com.vorto.challenge.DTO.DriverStateResponse;
import com.vorto.challenge.DTO.LoginOutcome;
import com.vorto.challenge.DTO.LoginRequest;


import java.util.Optional;
import java.util.UUID;

public interface DriverService {
    /**
     * Logs in or creates the driver.
     * Always returns the Driver and a created flag.
     */
    LoginOutcome loginOrCreate(LoginRequest request);
    Optional<DriverDto> get(UUID id);
    DriverStateResponse getDriverState(UUID driverId);

}
