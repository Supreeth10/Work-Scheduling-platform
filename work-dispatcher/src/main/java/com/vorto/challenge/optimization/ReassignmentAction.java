package com.vorto.challenge.optimization;

import java.util.UUID;

/**
 * Represents a change in assignment (assign, release, or no change).
 */
public class ReassignmentAction {
    private final UUID driverId;
    private final UUID loadId;
    private final ActionType type;
    
    public enum ActionType {
        /** Assign a load to a driver (new assignment) */
        ASSIGN,
        
        /** Release a load from a driver (remove assignment) */
        RELEASE,
        
        /** No change (load already assigned to this driver) */
        UNCHANGED
    }
    
    public ReassignmentAction(UUID driverId, UUID loadId, ActionType type) {
        this.driverId = driverId;
        this.loadId = loadId;
        this.type = type;
    }
    
    public static ReassignmentAction assign(UUID driverId, UUID loadId) {
        return new ReassignmentAction(driverId, loadId, ActionType.ASSIGN);
    }
    
    public static ReassignmentAction release(UUID driverId, UUID loadId) {
        return new ReassignmentAction(driverId, loadId, ActionType.RELEASE);
    }
    
    public static ReassignmentAction unchanged(UUID driverId, UUID loadId) {
        return new ReassignmentAction(driverId, loadId, ActionType.UNCHANGED);
    }
    
    public UUID getDriverId() {
        return driverId;
    }
    
    public UUID getLoadId() {
        return loadId;
    }
    
    public ActionType getType() {
        return type;
    }
    
    @Override
    public String toString() {
        return String.format("%s: Driver[%s] %s Load[%s]", 
                type, driverId, 
                type == ActionType.ASSIGN ? "→" : "↛", 
                loadId);
    }
}

