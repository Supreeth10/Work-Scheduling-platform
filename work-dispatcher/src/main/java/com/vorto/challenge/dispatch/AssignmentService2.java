package com.vorto.challenge.dispatch;

import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.LoadRepository;
import com.vorto.challenge.repository.ShiftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AssignmentService2 {
    private static final Logger log = LoggerFactory.getLogger(AssignmentService2.class);

    private final DriverRepository driverRepository;
    private final LoadRepository loadRepository;
    private final ShiftRepository shiftRepository;

    public AssignmentService2(DriverRepository driverRepository,
                              LoadRepository loadRepository,
                              ShiftRepository shiftRepository) {
        this.driverRepository = driverRepository;
        this.loadRepository = loadRepository;
        this.shiftRepository = shiftRepository;
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

        // TODO: Implement actual min-cost flow solver invocation
        
        log.info("[{}] Optimization pass completed", correlationId);
    }
}

