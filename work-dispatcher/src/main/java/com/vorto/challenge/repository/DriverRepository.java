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
    
    /**
     * Find all drivers who are on shift with active shift and no open loads.
     *
     * @return List of free drivers
     */
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
    """, nativeQuery = true)
    List<Driver> findFreeDrivers();
}
