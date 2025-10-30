package com.vorto.challenge.dispatch;

import com.vorto.challenge.config.DispatchOptimizerProperties;
import com.vorto.challenge.dispatch.knn.GeoKnnCandidateBuilder;
import com.vorto.challenge.dispatch.model.Candidates;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.LoadRepository;
import com.vorto.challenge.repository.ShiftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssignmentService2 {
    private static final Logger log = LoggerFactory.getLogger(AssignmentService2.class);

    private final DriverRepository driverRepository;
    private final LoadRepository loadRepository;
    private final ShiftRepository shiftRepository;
    private final GeoKnnCandidateBuilder candidateBuilder;
    private final DispatchOptimizerProperties properties;

    public AssignmentService2(DriverRepository driverRepository,
                              LoadRepository loadRepository,
                              ShiftRepository shiftRepository,
                              GeoKnnCandidateBuilder candidateBuilder,
                              DispatchOptimizerProperties properties) {
        this.driverRepository = driverRepository;
        this.loadRepository = loadRepository;
        this.shiftRepository = shiftRepository;
        this.candidateBuilder = candidateBuilder;
        this.properties = properties;
    }

    /**
     * Runs min-cost flow optimization. Currently logs entity counts.
     */
    public void runOptimization() {
        long driverCount = driverRepository.count();
        long loadCount = loadRepository.count();
        long shiftCount = shiftRepository.count();

        log.info("Running min-cost flow optimization. Drivers: {}, Loads: {}, Shifts: {}",
                driverCount, loadCount, shiftCount);

        // TODO: Implement actual min-cost flow solver invocation
    }

    /**
     * Runs a single optimization pass with correlation tracking.
     *
     * @param correlationId The correlation ID for tracing this optimization run
     */
    public void runOnce(String correlationId) {
        long driverCount = driverRepository.count();
        long loadCount = loadRepository.count();
        long shiftCount = shiftRepository.count();

        log.info("[{}] Running optimization pass. Drivers: {}, Loads: {}, Shifts: {}",
                correlationId, driverCount, loadCount, shiftCount);

        // Build candidates
        List<Candidates> candidates = buildCandidates(correlationId);
        
        log.info("[{}] Built {} candidate sets", correlationId, candidates.size());

        // TODO: Implement actual min-cost flow solver invocation
        
        log.info("[{}] Optimization pass completed", correlationId);
    }

    /**
     * Builds K-nearest neighbor candidates for optimization.
     *
     * @param correlationId The correlation ID for tracing
     * @return List of candidates
     */
    @Transactional(readOnly = true)
    public List<Candidates> buildCandidates(String correlationId) {
        // Fetch free drivers (on shift, active shift, no open loads)
        List<Driver> freeDrivers = driverRepository.findFreeDrivers();
        
        // Fetch unassigned loads (status = AWAITING_DRIVER)
        List<Load> unassignedLoads = loadRepository.findUnassignedLoads();
        
        log.info("[{}] Found {} free drivers and {} unassigned loads",
                correlationId, freeDrivers.size(), unassignedLoads.size());
        
        // Build candidates using K-NN
        int k1 = properties.getKnnK1();
        int k2 = properties.getKnnK2();
        
        List<Candidates> candidates = candidateBuilder.build(freeDrivers, unassignedLoads, k1, k2);
        
        log.info("[{}] Built {} candidate sets (k1={}, k2={})",
                correlationId, candidates.size(), k1, k2);
        
        // Log summary of candidates
        long driverCandidates = candidates.stream()
                .filter(c -> freeDrivers.stream().anyMatch(d -> d.getId().equals(c.entityId())))
                .count();
        long loadCandidates = candidates.size() - driverCandidates;
        
        log.debug("[{}] Candidates breakdown: {} for drivers, {} for loads",
                correlationId, driverCandidates, loadCandidates);
        
        return candidates;
    }
}

