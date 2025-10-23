package com.vorto.challenge.repository;

import com.vorto.challenge.model.Load;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface LoadRepository extends JpaRepository<Load, UUID> {


    @Query("""
  select l from Load l
  where l.assignedDriver.id = :driverId
    and l.status in :statuses
""")
    Optional<Load> findOpenByDriverId(UUID driverId,
                                      java.util.Collection<com.vorto.challenge.model.Load.Status> statuses);

    // Inline cleanup for expired reservations
    @Modifying
    @Query(value = """
        UPDATE loads
        SET status = 'AWAITING_DRIVER',
            assigned_driver_id = NULL,
            assigned_shift_id  = NULL,
            reservation_expires_at = NULL
        WHERE status = 'RESERVED'
          AND reservation_expires_at <= :now
        """, nativeQuery = true)
    int releaseExpiredReservations(Instant now);

    /**
     * Select the closest available AWAITING_DRIVER load (excludeId optional),
     * order by sphere distance (meters), and lock row to avoid races.
     */
    @Query(value = """
    WITH candidate AS (
      SELECT id
      FROM loads
      WHERE status = 'AWAITING_DRIVER'
        AND (:excludeId IS NULL OR id <> :excludeId)
      ORDER BY ST_Distance(
               pickup::geography,
               ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
      )
      LIMIT 1
      FOR UPDATE SKIP LOCKED
    )
    SELECT l.* FROM loads l
    JOIN candidate c ON c.id = l.id
    """, nativeQuery = true)
    Optional<Load> pickClosestAvailableForReservation(double lat, double lng, UUID excludeId);

    @EntityGraph(attributePaths = {"assignedDriver"})
    List<Load> findAll();

    @EntityGraph(attributePaths = {"assignedDriver"})
    List<Load> findAllByStatus(Load.Status status);

    @Query(value = """
        SELECT EXISTS (
          SELECT 1
          FROM loads l
           WHERE l.assigned_driver_id = :driverId
            AND l.status = 'IN_PROGRESS'
        )
        """, nativeQuery = true)
    boolean existsActiveByDriverId(@Param("driverId") UUID driverId);

    // 1) Lock and pick the closest candidate ID
    @Query(value = """
    WITH candidate AS (
      SELECT id
      FROM loads
      WHERE status = 'AWAITING_DRIVER'
        AND (:excludeId IS NULL OR id <> :excludeId)
      ORDER BY ST_Distance(
               pickup::geography,
               ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
      ), id
      LIMIT 1
      FOR UPDATE SKIP LOCKED
    )
    SELECT id FROM candidate
  """, nativeQuery = true)
    Optional<UUID> lockClosestAvailableId(double lat, double lng, UUID excludeId);

    // 2) Reserve that id (no return of row; JPA requires int/void)
    @Modifying
    @Query(value = """
    UPDATE loads
    SET status = 'RESERVED',
        assigned_driver_id = :driverId,
        assigned_shift_id  = :shiftId,
        reservation_expires_at = NOW() + (INTERVAL '1 second' * :reservationSeconds)
    WHERE id = :loadId
  """, nativeQuery = true)
    int reserveById(UUID loadId, UUID driverId, UUID shiftId, int reservationSeconds);

    @Query(value = """
  SELECT l.id AS id,
         ST_Distance(d.current_location::geography, l.pickup::geography) AS cost_meters
  FROM loads l
  JOIN drivers d ON d.id = :driverId
  WHERE l.status = 'AWAITING_DRIVER' AND l.assigned_driver_id IS NULL
  ORDER BY d.current_location <-> l.pickup
  LIMIT :k
""", nativeQuery = true)
    List<Map<String,Object>> findTopKNearestLoadsToDriver(@Param("driverId") UUID driverId,
                                                          @Param("k") int k);

    @Query(value = """
  WITH l1 AS (SELECT dropoff FROM loads WHERE id = :l1Id)
  SELECT l2.id AS id,
         ST_Distance(l1.dropoff::geography, l2.pickup::geography) AS chain_gap_meters
  FROM loads l2, l1
  WHERE l2.status = 'AWAITING_DRIVER' AND l2.assigned_driver_id IS NULL AND l2.id <> :l1Id
  ORDER BY l1.dropoff <-> l2.pickup
  LIMIT :m
""", nativeQuery = true)
    List<Map<String,Object>> findTopMFollowers(@Param("l1Id") UUID l1Id, @Param("m") int m);


    @Query(value = """
  SELECT id
  FROM loads
  WHERE id IN (:ids)
  FOR UPDATE SKIP LOCKED
""", nativeQuery = true)
    List<UUID> lockLoads(@Param("ids") List<UUID> ids);

    @Modifying
    @Query(value = """
  UPDATE loads
     SET assigned_driver_id = :driverId,
         status = 'RESERVED',
         assigned_shift_id = :shiftId,
         reservation_expires_at = NOW() + (INTERVAL '1 second' * :reservationSeconds)
   WHERE id = :loadId
     AND status = 'AWAITING_DRIVER'
     AND assigned_driver_id IS NULL
""", nativeQuery = true)
    int assignLoadIfFree(@Param("loadId") UUID loadId,
                         @Param("driverId") UUID driverId,
                         @Param("shiftId") UUID shiftId,
                         @Param("reservationSeconds") int reservationSeconds);
}

