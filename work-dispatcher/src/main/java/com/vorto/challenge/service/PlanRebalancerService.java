package com.vorto.challenge.service;

import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.LoadRepository;
import com.vorto.challenge.repository.ShiftRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PlanRebalancerService {
    private static final int RESERVATION_SECONDS = 120;

    private final DriverRepository driverRepo;
    private final LoadRepository loadRepo;
    private final ShiftRepository shiftRepo;
    private final EntityManager em;

    public PlanRebalancerService(DriverRepository driverRepo, ShiftRepository shiftRepo, LoadRepository loadRepo, EntityManager em) {
        this.driverRepo = driverRepo;
        this.shiftRepo = shiftRepo;
        this.loadRepo = loadRepo;
        this.em = em;
    }
    /** Call this right after a driver starts a shift (or becomes free). */
    @Transactional
    public void onNewDriverAvailable(UUID newDriverId) {
        var plannedRows = driverRepo.findAllWithPlanned();
        for (var pr : plannedRows) {
            UUID driverWithPlan = (UUID) pr.get("driver_id");
            UUID l2Id = (UUID) pr.get("planned");
            if (l2Id == null || driverWithPlan.equals(newDriverId)) continue;

            UUID l1Id = currentLoadId(driverWithPlan);
            if (l1Id == null) continue; // no current; skip x>y for now

            double x = dropoffToPickupMeters(l1Id, l2Id);
            double y = driverToPickupMeters(newDriverId, l2Id);

            if (x > y) {
                // Steal plan: clear from old driver
                driverRepo.clearPlannedNext(driverWithPlan, l2Id);

                // If new driver is free, assign L2 now; otherwise set as their plan
                UUID newCur = currentLoadId(newDriverId);
                if (newCur == null) {
                    // lock & reserve if still free
                    loadRepo.lockLoads(List.of(l2Id));
                    var shift = shiftRepo.findByDriverIdAndEndTimeIsNull(newDriverId).orElse(null);
                    if (shift != null) {
                        int ok = loadRepo.assignLoadIfFree(l2Id, newDriverId, shift.getId(), RESERVATION_SECONDS);
                        if (ok == 0) {
                            // lost the race; nothing else to do
                        }
                    }
                } else {
                    driverRepo.setPlannedNext(newDriverId, l2Id);
                }
            }
        }
    }

    private UUID currentLoadId(UUID driverId) {
        var q = em.createNativeQuery("""
          SELECT id FROM loads
          WHERE assigned_driver_id = :did
            AND status IN ('RESERVED','IN_PROGRESS')
          ORDER BY id
          LIMIT 1
        """);
        q.setParameter("did", driverId);
        @SuppressWarnings("unchecked")
        List<Object> ids = q.getResultList();
        return ids.isEmpty() ? null : (UUID) ids.get(0);
    }

    private double dropoffToPickupMeters(UUID l1Id, UUID l2Id) {
        var q = em.createNativeQuery("""
          SELECT ST_Distance(a.dropoff::geography, b.pickup::geography)
          FROM loads a, loads b
          WHERE a.id = :l1 AND b.id = :l2
        """);
        q.setParameter("l1", l1Id);
        q.setParameter("l2", l2Id);
        return ((Number) q.getSingleResult()).doubleValue();
    }

    private double driverToPickupMeters(UUID driverId, UUID loadId) {
        var q = em.createNativeQuery("""
          SELECT ST_Distance(d.current_location::geography, l.pickup::geography)
          FROM drivers d, loads l
          WHERE d.id = :did AND l.id = :lid
        """);
        q.setParameter("did", driverId);
        q.setParameter("lid", loadId);
        return ((Number) q.getSingleResult()).doubleValue();
    }

}
