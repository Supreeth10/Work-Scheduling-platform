package com.vorto.challenge.optimization;

/**
 * Represents events that trigger an optimization run.
 */
public enum OptimizationTrigger {
    /**
     * Triggered when a driver starts their shift
     */
    DRIVER_SHIFT_START,
    
    /**
     * Triggered when a new load is created
     */
    LOAD_CREATED,
    
    /**
     * Triggered when a driver completes a dropoff
     */
    DROPOFF_COMPLETE,
    
    /**
     * Manually triggered (e.g., for testing or admin actions)
     */
    MANUAL
}

