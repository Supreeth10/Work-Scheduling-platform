package com.vorto.challenge.repository;

import com.vorto.challenge.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    Optional<Driver> findByNameIgnoreCase(String username);
    boolean existsByNameIgnoreCase(String username);
}
