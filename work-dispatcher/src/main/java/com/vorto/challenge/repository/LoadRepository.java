package com.vorto.challenge.repository;

import com.vorto.challenge.model.Load;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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
}

