package com.vorto.challenge.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AssignmentCoordinator {
    private static final Logger log = LoggerFactory.getLogger(AssignmentCoordinator.class);

    /**
     * Serializes optimization runs. Currently a no-op placeholder.
     *
     * @param trigger The trigger source (e.g., "MANUAL", "SCHEDULED")
     */
    public void requestRun(String trigger) {
        log.info("Assignment optimization run requested. Trigger: {}", trigger);
        // TODO: Implement serialization logic and actual optimization execution
    }
}

