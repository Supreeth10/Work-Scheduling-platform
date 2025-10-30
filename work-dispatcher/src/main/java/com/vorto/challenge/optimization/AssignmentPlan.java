package com.vorto.challenge.optimization;

import java.util.*;

/**
 * The result of an optimization run containing optimal assignments.
 */
public class AssignmentPlan {
    private final Map<UUID, LoadSequence> driverAssignments;
    private final double totalDeadheadMiles;
    private final List<ReassignmentAction> changes;
    
    public AssignmentPlan(
            Map<UUID, LoadSequence> driverAssignments,
            double totalDeadheadMiles,
            List<ReassignmentAction> changes) {
        this.driverAssignments = Map.copyOf(driverAssignments);
        this.totalDeadheadMiles = totalDeadheadMiles;
        this.changes = List.copyOf(changes);
    }
    
    /**
     * Builder for constructing an AssignmentPlan
     */
    public static class Builder {
        private final Map<UUID, LoadSequence> assignments = new HashMap<>();
        private final List<ReassignmentAction> changes = new ArrayList<>();
        private double totalDeadhead = 0.0;
        
        public Builder addAssignment(LoadSequence sequence) {
            assignments.put(sequence.getDriverId(), sequence);
            totalDeadhead += sequence.getDeadheadMiles();
            return this;
        }
        
        public Builder addChange(ReassignmentAction change) {
            changes.add(change);
            return this;
        }
        
        public Builder addChanges(List<ReassignmentAction> changes) {
            this.changes.addAll(changes);
            return this;
        }
        
        public AssignmentPlan build() {
            return new AssignmentPlan(assignments, totalDeadhead, changes);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * @return Map of driver ID to their assigned load sequence
     */
    public Map<UUID, LoadSequence> getDriverAssignments() {
        return driverAssignments;
    }
    
    /**
     * Get the assignment for a specific driver
     * @param driverId Driver ID
     * @return LoadSequence for this driver, or null if no assignment
     */
    public LoadSequence getAssignmentForDriver(UUID driverId) {
        return driverAssignments.get(driverId);
    }
    
    /**
     * @return Total deadhead miles across all assignments
     */
    public double getTotalDeadheadMiles() {
        return totalDeadheadMiles;
    }
    
    /**
     * @return List of changes compared to previous state
     */
    public List<ReassignmentAction> getChanges() {
        return changes;
    }
    
    /**
     * @return All load IDs that are assigned in this plan
     */
    public Set<UUID> getAssignedLoadIds() {
        Set<UUID> loadIds = new HashSet<>();
        for (LoadSequence seq : driverAssignments.values()) {
            loadIds.addAll(seq.getLoadIds());
        }
        return loadIds;
    }
    
    /**
     * @return true if this plan has no assignments
     */
    public boolean isEmpty() {
        return driverAssignments.isEmpty();
    }
    
    /**
     * @return Number of drivers with assignments
     */
    public int getAssignedDriverCount() {
        return (int) driverAssignments.values().stream()
                .filter(seq -> !seq.isEmpty())
                .count();
    }
    
    /**
     * @return Total number of loads assigned
     */
    public int getAssignedLoadCount() {
        return driverAssignments.values().stream()
                .mapToInt(LoadSequence::size)
                .sum();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("AssignmentPlan [%d drivers, %d loads, %.2f mi total deadhead]\n",
                getAssignedDriverCount(), getAssignedLoadCount(), totalDeadheadMiles));
        
        for (LoadSequence seq : driverAssignments.values()) {
            if (!seq.isEmpty()) {
                sb.append("  ").append(seq.toString()).append("\n");
            }
        }
        
        if (!changes.isEmpty()) {
            sb.append(String.format("Changes: %d actions\n", changes.size()));
        }
        
        return sb.toString();
    }
}

