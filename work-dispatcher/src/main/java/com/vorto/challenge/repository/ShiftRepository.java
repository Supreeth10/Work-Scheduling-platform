package com.vorto.challenge.repository;

import com.vorto.challenge.model.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, UUID> {
    // With UNIQUE driver_id, this returns at most one row (the current shift row).
    Optional<Shift> findByDriverId(UUID driverId);
    boolean existsByDriverId(UUID driverId);
    // True iff there's an active shift
    boolean existsByDriverIdAndEndTimeIsNull(UUID driverId);
    // Retrieve the active shift (if you need it)
    Optional<Shift> findFirstByDriverIdAndEndTimeIsNull(UUID driverId);
    @Query("""
      select s from Shift s
      where s.driver.id = :driverId and s.endTime is null
    """)
    Optional<Shift> findActiveShift(UUID driverId);
}
