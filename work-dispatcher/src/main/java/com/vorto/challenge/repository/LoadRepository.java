package com.vorto.challenge.repository;

import com.vorto.challenge.model.Load;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LoadRepository extends JpaRepository<Load, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Load l where l.id = :id")
    Optional<Load> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByAssignedDriver_IdAndStatusNot(UUID driverId, Load.Status status);
}
