package com.vorto.challenge.dispatch;

import org.springframework.stereotype.Component;

/**
 * Utility component to trigger optimization runs.
 * This allows other services to request optimization without direct coupling to AssignmentCoordinator.
 */
@Component
public class AssignmentCoordinatorTrigger {
    
    private final AssignmentCoordinator coordinator;

    public AssignmentCoordinatorTrigger(AssignmentCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Triggers an optimization run.
     *
     * @param reason The reason for triggering (e.g., "LOAD_CREATED", "SHIFT_STARTED")
     * @param correlationId The correlation ID for tracing
     */
    public void trigger(String reason, String correlationId) {
        coordinator.requestRun(reason, correlationId);
    }
}

