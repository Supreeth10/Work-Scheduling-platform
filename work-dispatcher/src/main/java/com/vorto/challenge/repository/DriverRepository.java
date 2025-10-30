package com.vorto.challenge.repository;

import com.vorto.challenge.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    Optional<Driver> findByNameIgnoreCase(String username);
    boolean existsByNameIgnoreCase(String username);
    
    /**
     * Find all drivers that are eligible for assignment.
     * Eligible means: on-shift, with known location, and has an active shift.
     * 
     * Note: This INCLUDES drivers who already have assignments (for reassignment in optimization).
     * 
     * @return List of eligible drivers
     */
    @Query("""
        SELECT d FROM Driver d
        WHERE d.onShift = true
          AND d.currentLocation IS NOT NULL
          AND EXISTS (
              SELECT 1 FROM Shift s 
              WHERE s.driver = d AND s.endTime IS NULL
          )
    """)
    List<Driver> findAllEligibleForAssignment();
    
    /**
     * @deprecated Replaced by OptimizationService for global assignment.
     * This method will be removed in a future version.
     * Use findAllEligibleForAssignment() and OptimizationService instead.
     */
    @Deprecated
    @Query(value = """
    SELECT d.*
    FROM drivers d
    WHERE d.on_shift = TRUE
      AND d.current_location IS NOT NULL
      -- has an active shift
      AND EXISTS (
          SELECT 1 FROM shifts s
          WHERE s.driver_id = d.id
            AND s.end_time IS NULL
      )
      -- no open load (RESERVED/IN_PROGRESS)
      AND NOT EXISTS (
          SELECT 1 FROM loads l
          WHERE l.assigned_driver_id = d.id
            AND l.status IN ('RESERVED','IN_PROGRESS')
      )
    ORDER BY ST_Distance(
              d.current_location::geography,
              ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
    )
    LIMIT 1
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    Optional<Driver> findClosestAvailableDriver(@Param("lat") double lat,
                                                @Param("lng") double lng);
}
