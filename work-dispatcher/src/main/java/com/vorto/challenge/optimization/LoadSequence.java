package com.vorto.challenge.optimization;

import java.util.List;
import java.util.UUID;

/**
 * Represents a sequence of loads assigned to a single driver.
 * Can contain 0 (idle), 1 (single), or 2 (chained) loads.
 */
public class LoadSequence {
    private final UUID driverId;
    private final List<UUID> loadIds;
    private final double deadheadMiles;
    
    public LoadSequence(UUID driverId, List<UUID> loadIds, double deadheadMiles) {
        if (loadIds.size() > 2) {
            throw new IllegalArgumentException("Driver can have at most 2 loads");
        }
        this.driverId = driverId;
        this.loadIds = List.copyOf(loadIds);
        this.deadheadMiles = deadheadMiles;
    }
    
    /**
     * Create an empty sequence (driver with no assignment)
     */
    public static LoadSequence empty(UUID driverId) {
        return new LoadSequence(driverId, List.of(), 0.0);
    }
    
    /**
     * Create a single-load sequence
     */
    public static LoadSequence single(UUID driverId, UUID loadId, double deadheadMiles) {
        return new LoadSequence(driverId, List.of(loadId), deadheadMiles);
    }
    
    /**
     * Create a two-load chained sequence
     */
    public static LoadSequence chained(UUID driverId, UUID firstLoadId, UUID secondLoadId, double deadheadMiles) {
        return new LoadSequence(driverId, List.of(firstLoadId, secondLoadId), deadheadMiles);
    }
    
    public UUID getDriverId() {
        return driverId;
    }
    
    public List<UUID> getLoadIds() {
        return loadIds;
    }
    
    public double getDeadheadMiles() {
        return deadheadMiles;
    }
    
    /**
     * @return true if this sequence contains two loads (is a chain)
     */
    public boolean isChained() {
        return loadIds.size() == 2;
    }
    
    /**
     * @return true if this sequence has no loads
     */
    public boolean isEmpty() {
        return loadIds.isEmpty();
    }
    
    /**
     * @return Number of loads in this sequence (0, 1, or 2)
     */
    public int size() {
        return loadIds.size();
    }
    
    @Override
    public String toString() {
        if (isEmpty()) {
            return String.format("Driver[%s] → idle", driverId);
        } else if (isChained()) {
            return String.format("Driver[%s] → Load[%s] → Load[%s] (%.2f mi)", 
                    driverId, loadIds.get(0), loadIds.get(1), deadheadMiles);
        } else {
            return String.format("Driver[%s] → Load[%s] (%.2f mi)", 
                    driverId, loadIds.get(0), deadheadMiles);
        }
    }
}

