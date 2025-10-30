package com.vorto.challenge.optimization;

import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;

import java.util.List;
import java.util.UUID;

/**
 * Input context for the optimizer containing current system state.
 */
public class OptimizationContext {
    private final List<Driver> eligibleDrivers;
    private final List<Load> assignableLoads;
    private final List<Load> protectedLoads;
    private final OptimizationTrigger trigger;
    private final UUID triggeringEntityId;
    
    public OptimizationContext(
            List<Driver> eligibleDrivers,
            List<Load> assignableLoads,
            List<Load> protectedLoads,
            OptimizationTrigger trigger,
            UUID triggeringEntityId) {
        this.eligibleDrivers = eligibleDrivers;
        this.assignableLoads = assignableLoads;
        this.protectedLoads = protectedLoads;
        this.trigger = trigger;
        this.triggeringEntityId = triggeringEntityId;
    }
    
    /**
     * @return Drivers that are on-shift with known location (eligible for assignment)
     */
    public List<Driver> getEligibleDrivers() {
        return eligibleDrivers;
    }
    
    /**
     * @return Loads that can be assigned (AWAITING_DRIVER or RESERVED)
     */
    public List<Load> getAssignableLoads() {
        return assignableLoads;
    }
    
    /**
     * @return Loads that are IN_PROGRESS (cannot be reassigned)
     */
    public List<Load> getProtectedLoads() {
        return protectedLoads;
    }
    
    /**
     * @return The event that triggered this optimization
     */
    public OptimizationTrigger getTrigger() {
        return trigger;
    }
    
    /**
     * @return ID of the entity that triggered this optimization (driver or load)
     */
    public UUID getTriggeringEntityId() {
        return triggeringEntityId;
    }
}

