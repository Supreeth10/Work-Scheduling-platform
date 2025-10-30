package com.vorto.challenge.optimization;

import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates all valid driver-load sequences for optimization.
 * 
 * For each driver, creates:
 * - One empty sequence (no assignment)
 * - One single-load sequence for each load
 * - One chained sequence for each valid pair of loads
 * 
 * Note: This generates the "menu" of options (polynomial work: O(N × M²)).
 * The MIP solver then intelligently selects the best combination using branch-and-bound,
 * NOT by trying all possible combinations (which would be exponential: O(S^N)).
 */
@Component
public class SequenceGenerator {
    
    private final DistanceCalculator distanceCalculator;
    
    public SequenceGenerator(DistanceCalculator distanceCalculator) {
        this.distanceCalculator = distanceCalculator;
    }
    
    /**
     * Generate all valid sequences for the given drivers and loads.
     * 
     * For N drivers and M loads, generates:
     * - N empty sequences (one per driver)
     * - N × M single-load sequences
     * - N × M × (M-1) chained sequences (ordered pairs, L1→L2 ≠ L2→L1)
     * 
     * Total sequences: N × (1 + M + M×(M-1))
     * Example: 10 drivers, 10 loads → 10 × 101 = 1,010 sequences
     * 
     * @param drivers List of eligible drivers
     * @param loads List of assignable loads
     * @return List of all possible sequences with computed deadhead costs
     */
    public List<DriverLoadSequence> generateAllSequences(List<Driver> drivers, List<Load> loads) {
        List<DriverLoadSequence> sequences = new ArrayList<>();
        
        for (Driver driver : drivers) {
            // 1. Empty sequence (driver idle)
            sequences.add(DriverLoadSequence.empty(driver));
            
            // 2. Single-load sequences
            for (Load load : loads) {
                double deadhead = distanceCalculator.calculateDistance(
                        driver.getCurrentLocation(),
                        load.getPickup()
                );
                sequences.add(DriverLoadSequence.single(driver, load, deadhead));
            }
            
            // 3. Chained sequences (two loads)
            for (int i = 0; i < loads.size(); i++) {
                Load firstLoad = loads.get(i);
                for (int j = 0; j < loads.size(); j++) {
                    if (i == j) continue; // Skip same load
                    
                    Load secondLoad = loads.get(j);
                    
                    double deadhead = distanceCalculator.calculateDeadheadDistance(
                            driver.getCurrentLocation(),
                            firstLoad.getPickup(),
                            firstLoad.getDropoff(),
                            secondLoad.getPickup()
                    );
                    
                    sequences.add(DriverLoadSequence.chained(driver, firstLoad, secondLoad, deadhead));
                }
            }
        }
        
        return sequences;
    }
    
    /**
     * Internal representation of a driver-load sequence with full entity references.
     * Used during sequence generation and MIP model building.
     * 
     * This class bridges the gap between domain entities (Driver, Load) and 
     * the optimization DTOs (LoadSequence).
     */
    public static class DriverLoadSequence {
        private final Driver driver;
        private final List<Load> loads;
        private final double deadheadMiles;
        private final String id; // Unique identifier for MIP variable naming
        
        private DriverLoadSequence(Driver driver, List<Load> loads, double deadheadMiles) {
            this.driver = driver;
            this.loads = List.copyOf(loads);
            this.deadheadMiles = deadheadMiles;
            
            // Generate unique ID for this sequence (used as MIP variable name)
            if (loads.isEmpty()) {
                this.id = String.format("D%s_IDLE", shortId(driver.getId()));
            } else if (loads.size() == 1) {
                this.id = String.format("D%s_L%s", 
                        shortId(driver.getId()),
                        shortId(loads.get(0).getId()));
            } else {
                this.id = String.format("D%s_L%s_L%s",
                        shortId(driver.getId()),
                        shortId(loads.get(0).getId()),
                        shortId(loads.get(1).getId()));
            }
        }
        
        /**
         * Get first 8 characters of UUID for readable IDs
         */
        private static String shortId(UUID uuid) {
            return uuid.toString().substring(0, 8);
        }
        
        public static DriverLoadSequence empty(Driver driver) {
            return new DriverLoadSequence(driver, List.of(), 0.0);
        }
        
        public static DriverLoadSequence single(Driver driver, Load load, double deadheadMiles) {
            return new DriverLoadSequence(driver, List.of(load), deadheadMiles);
        }
        
        public static DriverLoadSequence chained(Driver driver, Load firstLoad, Load secondLoad, double deadheadMiles) {
            return new DriverLoadSequence(driver, List.of(firstLoad, secondLoad), deadheadMiles);
        }
        
        public Driver getDriver() {
            return driver;
        }
        
        public List<Load> getLoads() {
            return loads;
        }
        
        public double getDeadheadMiles() {
            return deadheadMiles;
        }
        
        public String getId() {
            return id;
        }
        
        public boolean isEmpty() {
            return loads.isEmpty();
        }
        
        public boolean isChained() {
            return loads.size() == 2;
        }
        
        /**
         * Convert to LoadSequence DTO (with UUIDs only, no entity references)
         */
        public LoadSequence toLoadSequence() {
            List<UUID> loadIds = loads.stream()
                    .map(Load::getId)
                    .toList();
            return new LoadSequence(driver.getId(), loadIds, deadheadMiles);
        }
        
        /**
         * Check if this sequence contains a specific load
         */
        public boolean containsLoad(Load load) {
            return loads.stream().anyMatch(l -> l.getId().equals(load.getId()));
        }
        
        @Override
        public String toString() {
            if (isEmpty()) {
                return String.format("Driver[%s] → idle", driver.getName());
            } else if (isChained()) {
                return String.format("Driver[%s] → Load[%s→%s] (%.2f mi)",
                        driver.getName(),
                        shortId(loads.get(0).getId()),
                        shortId(loads.get(1).getId()),
                        deadheadMiles);
            } else {
                return String.format("Driver[%s] → Load[%s] (%.2f mi)",
                        driver.getName(),
                        shortId(loads.get(0).getId()),
                        deadheadMiles);
            }
        }
    }
}

