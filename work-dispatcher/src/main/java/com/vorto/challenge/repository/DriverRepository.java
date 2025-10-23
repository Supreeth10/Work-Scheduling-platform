package com.vorto.challenge.repository;

import com.vorto.challenge.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
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

    @Query(value = """
  SELECT d.id
  FROM drivers d
  WHERE d.on_shift = TRUE
    AND d.current_location IS NOT NULL
    AND EXISTS (SELECT 1 FROM shifts s WHERE s.driver_id = d.id AND s.end_time IS NULL)
    AND NOT EXISTS (
      SELECT 1 FROM loads l
       WHERE l.assigned_driver_id = d.id
         AND l.status IN ('RESERVED','IN_PROGRESS')
    )
""", nativeQuery = true)
    List<UUID> findAvailableDriverIds();

    @Modifying
    @Query(value = """
  UPDATE drivers SET planned_next_load_id = :plannedId
  WHERE id = :driverId
""", nativeQuery = true)
    int setPlannedNext(@Param("driverId") UUID driverId, @Param("plannedId") UUID plannedId);

    @Modifying
    @Query(value = """
  UPDATE drivers SET planned_next_load_id = NULL
  WHERE id = :driverId AND planned_next_load_id = :plannedId
""", nativeQuery = true)
    int clearPlannedNext(@Param("driverId") UUID driverId, @Param("plannedId") UUID plannedId);

    @Query(value = """
  SELECT d.id AS driver_id, d.planned_next_load_id AS planned
  FROM drivers d
  WHERE d.planned_next_load_id IS NOT NULL
""", nativeQuery = true)
    List<Map<String, Object>> findAllWithPlanned();

    @Query(value = """
  SELECT d.id
  FROM drivers d
  WHERE d.on_shift = TRUE
    AND d.current_location IS NOT NULL
    AND EXISTS (SELECT 1 FROM shifts s WHERE s.driver_id = d.id AND s.end_time IS NULL)
    AND NOT EXISTS (
      SELECT 1 FROM loads l
      WHERE l.assigned_driver_id = d.id
        AND l.status IN ('RESERVED','IN_PROGRESS')
    )
  ORDER BY d.current_location <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
  LIMIT :limit
""", nativeQuery = true)
    List<UUID> findNearestAvailableDriverIdsToPoint(@Param("lat") double lat,
                                                    @Param("lng") double lng,
                                                    @Param("limit") int limit);

}
