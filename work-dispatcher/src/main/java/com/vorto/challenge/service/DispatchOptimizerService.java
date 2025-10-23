package com.vorto.challenge.service;

import com.vorto.challenge.DTO.Option;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.LoadRepository;
import com.vorto.challenge.repository.ShiftRepository;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DispatchOptimizerService {
    private static final int K = 5; // shortlist nearest loads per driver
    private static final int M = 3; // shortlist followers per L1

    private static final int RESERVATION_SECONDS = 120;

    private final DriverRepository driverRepo;
    private final ShiftRepository shiftRepo;
    private final LoadRepository loadRepo;
    private final EntityManager em;

    public DispatchOptimizerService(DriverRepository driverRepo, ShiftRepository shiftRepo, LoadRepository loadRepo, EntityManager em) {
        this.driverRepo = driverRepo;
        this.shiftRepo = shiftRepo;
        this.loadRepo = loadRepo;
        this.em = em;
    }

    /** Run optimizer for a set of available drivers (on-shift, idle). */
    @Transactional
    public void runForAvailableDrivers(Collection<UUID> driverIds) {
        if (driverIds == null || driverIds.isEmpty()) return;

        // 1) Compute best option per driver
        List<Option> options = new ArrayList<>();
        for (UUID d : driverIds) {
            Option best = computeBestOptionForDriver(d, K, M);
            if (best != null) options.add(best);
        }
        if (options.isEmpty()) return;

        // 2) Resolve conflicts (unique driver & unique loads) by ascending cost
        options.sort(Comparator.comparingDouble(Option::totalCostMeters));
        Set<UUID> usedDrivers = new HashSet<>();
        Set<UUID> usedLoads   = new HashSet<>();

        for (Option opt : options) {
            if (!usedDrivers.add(opt.driverId())) continue;
            if (!usedLoads.add(opt.l1Id())) { usedDrivers.remove(opt.driverId()); continue; }
            if (opt.l2IdOrNull() != null && !usedLoads.add(opt.l2IdOrNull())) {
                usedDrivers.remove(opt.driverId());
                usedLoads.remove(opt.l1Id());
                continue;
            }
            // 3) Try to assign (L1) and plan (L2)
            txAssignAndPlan(opt);
        }
    }

    private Option computeBestOptionForDriver(UUID driverId, int k, int m) {
        var shortlist = loadRepo.findTopKNearestLoadsToDriver(driverId, k);
        Option best = null;

        for (var row : shortlist) {
            UUID l1 = (UUID) row.get("id");
            double costSingle = ((Number) row.get("cost_meters")).doubleValue();

            // Single option
            best = better(best, new Option(driverId, l1, null, costSingle));

            // Followers near L1.dropoff
            var followers = loadRepo.findTopMFollowers(l1, m);
            for (var f : followers) {
                UUID l2 = (UUID) f.get("id");
                double gap = ((Number) f.get("chain_gap_meters")).doubleValue();
                best = better(best, new Option(driverId, l1, l2, costSingle + gap));
            }
        }
        return best;
    }

    @Transactional
    protected void txAssignAndPlan(Option opt) {
        // lock rows to avoid races
        List<UUID> toLock = new ArrayList<>();
        toLock.add(opt.l1Id());
        if (opt.l2IdOrNull() != null) toLock.add(opt.l2IdOrNull());
        if (!toLock.isEmpty()) {
            loadRepo.lockLoads(toLock);
        }

        // Active shift is required to reserve
        var shift = shiftRepo.findByDriverIdAndEndTimeIsNull(opt.driverId()).orElse(null);
        if (shift == null) return;

        // Assign L1 if still free
        int ok = loadRepo.assignLoadIfFree(opt.l1Id(), opt.driverId(), shift.getId(), RESERVATION_SECONDS);
        if (ok == 0) return; // someone took it

        // Plan L2 (soft, stays AWAITING_DRIVER)
        if (opt.l2IdOrNull() != null) {
            driverRepo.setPlannedNext(opt.driverId(), opt.l2IdOrNull());
        }
    }

    private static Option pickMin(Option a, Option b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.totalCostMeters() <= b.totalCostMeters() ? a : b;
    }

    private static final double EPS = 1e-6;

    /** Prefer lower cost; on a tie (within EPS), prefer a CHAIN (non-null l2IdOrNull). */
    private static Option better(Option a, Option b) {
        if (a == null) return b;
        if (b == null) return a;

        double da = a.totalCostMeters();
        double db = b.totalCostMeters();

        if (Math.abs(da - db) > EPS) {
            return (da < db) ? a : b;
        }

        // tie: prefer chain
        boolean aChain = a.l2IdOrNull() != null;
        boolean bChain = b.l2IdOrNull() != null;
        if (aChain == bChain) return a; // stable (keep earlier)
        return bChain ? b : a;
    }
}
