package com.vorto.challenge.service;


import com.vorto.challenge.DTO.LoginRequest;
import com.vorto.challenge.model.Driver;

public interface DriverService {
    /**
     * Logs in or creates the driver.
     * @return If created, the new Driver entity; if already existed, return null.
     */
    Driver loginOrCreate(LoginRequest request);
}
