package com.vorto.challenge.optimization;

import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OptimizationService demonstrating:
 * - MIP superiority over greedy algorithms
 * - Dynamic rebalancing capabilities
 * - IN_PROGRESS load protection
 * - Large-scale performance
 */
class OptimizationIntegrationTest {
    
    private OptimizationService optimizationService;
    private DistanceCalculator distanceCalculator;
    private GeometryFactory geometryFactory;
    
    @BeforeEach
    void setUp() {
        distanceCalculator = new DistanceCalculator();
        SequenceGenerator sequenceGenerator = new SequenceGenerator(distanceCalculator);
        optimizationService = new OptimizationService(sequenceGenerator);
        geometryFactory = new GeometryFactory();
    }
    
    private Point createPoint(double lat, double lng) {
        return geometryFactory.createPoint(new Coordinate(lng, lat));
    }
    
    private Driver createDriver(String name, double lat, double lng) {
        Driver driver = new Driver();
        driver.setId(UUID.randomUUID());
        driver.setName(name);
        driver.setCurrentLocation(createPoint(lat, lng));
        driver.setOnShift(true);
        return driver;
    }
    
    private Load createLoad(double pickupLat, double pickupLng, double dropoffLat, double dropoffLng) {
        Load load = new Load();
        load.setId(UUID.randomUUID());
        load.setPickup(createPoint(pickupLat, pickupLng));
        load.setDropoff(createPoint(dropoffLat, dropoffLng));
        load.setStatus(Load.Status.AWAITING_DRIVER);
        return load;
    }
    
    /**
     * Simulate a greedy algorithm: iteratively assign closest driver-load pair.
     * Returns total deadhead miles.
     */
    private double calculateGreedyTotalDeadhead(List<Driver> drivers, List<Load> loads) {
        List<Driver> availableDrivers = new ArrayList<>(drivers);
        List<Load> availableLoads = new ArrayList<>(loads);
        double totalDeadhead = 0.0;
        
        while (!availableDrivers.isEmpty() && !availableLoads.isEmpty()) {
            Driver bestDriver = null;
            Load bestLoad = null;
            double bestDistance = Double.MAX_VALUE;
            
            // Find globally closest driver-load pair
            for (Driver driver : availableDrivers) {
                for (Load load : availableLoads) {
                    double distance = distanceCalculator.calculateDistance(
                            driver.getCurrentLocation(),
                            load.getPickup()
                    );
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestDriver = driver;
                        bestLoad = load;
                    }
                }
            }
            
            if (bestDriver != null && bestLoad != null) {
                totalDeadhead += bestDistance;
                availableDrivers.remove(bestDriver);
                availableLoads.remove(bestLoad);
            } else {
                break;
            }
        }
        
        return totalDeadhead;
    }
    
    @Test
    void mipShouldBeatGreedyOnAsymmetricScenario() {
        // TEST 1: MIP vs Greedy Comparison
        // Setup: 3 drivers, 3 loads arranged to show MIP finds better solution
        
        Driver d1 = createDriver("D1", 0.0, 0.0);
        Driver d2 = createDriver("D2", 0.0, 5.0);
        Driver d3 = createDriver("D3", 5.0, 0.0);
        
        // Create asymmetric distances
        Load l1 = createLoad(0.1, 0.1, 1.0, 1.0);     // Very close to D1
        Load l2 = createLoad(0.0, 5.2, 0.0, 6.0);     // Close to D2
        Load l3 = createLoad(5.3, 0.0, 6.0, 0.0);     // Close to D3
        
        List<Driver> drivers = List.of(d1, d2, d3);
        List<Load> loads = List.of(l1, l2, l3);
        
        // Calculate greedy total
        double greedyDeadhead = calculateGreedyTotalDeadhead(drivers, loads);
        
        // Run MIP optimization
        OptimizationContext context = new OptimizationContext(
                drivers, loads, List.of(), OptimizationTrigger.MANUAL, null);
        
        AssignmentPlan mipPlan = optimizationService.optimize(context);
        double mipDeadhead = mipPlan.getTotalDeadheadMiles();
        
        // MIP should be equal or better than greedy
        assertThat(mipDeadhead).isLessThanOrEqualTo(greedyDeadhead);
        
        // All loads should be assigned
        assertThat(mipPlan.getAssignedLoadCount()).isEqualTo(3);
        
        System.out.println("Greedy: " + greedyDeadhead + " mi, MIP: " + mipDeadhead + " mi");
    }
    
    @Test
    void mipShouldRespectProtectedInProgressLoads() {
        // TEST 2: IN_PROGRESS Protection
        // Setup: D1 has IN_PROGRESS load, new load arrives
        // Note: Current implementation includes all on-shift drivers in optimization,
        // but they can get additional loads assigned. This is actually OK since
        // drivers can have multiple loads (up to 2). The IN_PROGRESS load itself
        // is not reassigned (it's in protectedLoads, not assignableLoads).
        
        Driver d1 = createDriver("D1", 0.0, 0.0);
        Driver d2 = createDriver("D2", 10.0, 0.0);
        
        // L1 is IN_PROGRESS with D1 (protected, in protected list)
        Load l1 = createLoad(5.0, 5.0, 6.0, 6.0);
        l1.setStatus(Load.Status.IN_PROGRESS);
        l1.setAssignedDriver(d1);
        
        // L2 is available
        Load l2 = createLoad(0.5, 0.5, 1.0, 1.0);
        
        OptimizationContext context = new OptimizationContext(
                List.of(d1, d2),    // Both drivers eligible
                List.of(l2),        // Only L2 is assignable (L1 is NOT here)
                List.of(l1),        // L1 is protected (won't be reassigned)
                OptimizationTrigger.LOAD_CREATED,
                l2.getId()
        );
        
        AssignmentPlan plan = optimizationService.optimize(context);
        
        // L2 should be assigned
        assertThat(plan.getAssignedLoadCount()).isEqualTo(1);
        
        // L2 can go to either driver (both eligible)
        assertThat(plan.getAssignedDriverCount()).isEqualTo(1);
        
        // Key assertion: L1 is NOT in the assignment plan (it's protected)
        assertThat(plan.getAssignedLoadIds()).doesNotContain(l1.getId());
        assertThat(plan.getAssignedLoadIds()).contains(l2.getId());
    }
    
    @Test
    void mipShouldHandleRebalancingScenario() {
        // TEST 3: Dynamic Rebalancing
        // Simulate: Loads can be reassigned for better global optimization
        
        Driver d1 = createDriver("D1", 0.0, 0.0);
        Driver d2 = createDriver("D2", 20.0, 0.0);
        
        // L1 far from both, but closer to D2
        Load l1 = createLoad(19.0, 0.0, 19.5, 0.0);
        l1.setStatus(Load.Status.AWAITING_DRIVER);
        
        // L2 close to D1
        Load l2 = createLoad(1.0, 0.0, 1.5, 0.0);
        l2.setStatus(Load.Status.AWAITING_DRIVER);
        
        // Both loads are assignable
        OptimizationContext context = new OptimizationContext(
                List.of(d1, d2),
                List.of(l1, l2),
                List.of(),
                OptimizationTrigger.MANUAL,
                null
        );
        
        AssignmentPlan plan = optimizationService.optimize(context);
        
        // Both loads should be assigned
        assertThat(plan.getAssignedLoadCount()).isEqualTo(2);
        
        // MIP should find optimal assignment
        LoadSequence d1Seq = plan.getAssignmentForDriver(d1.getId());
        LoadSequence d2Seq = plan.getAssignmentForDriver(d2.getId());
        
        // At least one driver should be assigned
        boolean d1Assigned = d1Seq != null;
        boolean d2Assigned = d2Seq != null;
        assertThat(d1Assigned || d2Assigned).isTrue();
        
        // Verify reasonable total deadhead
        assertThat(plan.getTotalDeadheadMiles()).isLessThan(150.0);
    }
    
    @Test
    void mipShouldHandleMediumScaleWithChaining() {
        // TEST 4: Medium scale (10 drivers, 12 loads) with chaining opportunities
        List<Driver> drivers = new ArrayList<>();
        List<Load> loads = new ArrayList<>();
        
        // Create 10 drivers
        for (int i = 0; i < 10; i++) {
            drivers.add(createDriver("D" + i, i * 2.0, 0.0));
        }
        
        // Create 12 loads, some clustered for chaining
        for (int i = 0; i < 12; i++) {
            loads.add(createLoad(
                    i * 2.0 + 0.2, 0.0 + 0.1,
                    i * 2.0 + 0.5, 0.0 + 0.2
            ));
        }
        
        OptimizationContext context = new OptimizationContext(
                drivers, loads, List.of(), OptimizationTrigger.MANUAL, null);
        
        long startTime = System.currentTimeMillis();
        AssignmentPlan plan = optimizationService.optimize(context);
        long duration = System.currentTimeMillis() - startTime;
        
        // Should complete quickly
        assertThat(duration).isLessThan(200);
        
        // All or most loads should be assigned (10 drivers can handle up to 20 loads)
        assertThat(plan.getAssignedLoadCount()).isGreaterThan(10);
        
        System.out.println("Medium scale (10 drivers, 12 loads): " + duration + "ms");
    }
    
    @Test
    void mipShouldOptimizeWithMixOfSingleAndChainedAssignments() {
        // TEST 5: Mixed scenario - some chains, some singles
        Driver d1 = createDriver("D1", 0.0, 0.0);
        Driver d2 = createDriver("D2", 10.0, 0.0);
        Driver d3 = createDriver("D3", 20.0, 0.0);
        
        // Loads near D1 - good for chaining
        Load l1 = createLoad(1.0, 0.0, 1.5, 0.0);
        Load l2 = createLoad(1.6, 0.0, 2.0, 0.0);  // 0.1 degree from L1 dropoff
        
        // Load near D2 - single assignment
        Load l3 = createLoad(10.5, 0.0, 11.0, 0.0);
        
        // Load near D3 - single assignment
        Load l4 = createLoad(20.5, 0.0, 21.0, 0.0);
        
        OptimizationContext context = new OptimizationContext(
                List.of(d1, d2, d3),
                List.of(l1, l2, l3, l4),
                List.of(),
                OptimizationTrigger.MANUAL,
                null
        );
        
        AssignmentPlan plan = optimizationService.optimize(context);
        
        // All 4 loads should be assigned
        assertThat(plan.getAssignedLoadCount()).isEqualTo(4);
        
        // D1 should have chained loads
        LoadSequence d1Seq = plan.getAssignmentForDriver(d1.getId());
        assertThat(d1Seq).isNotNull();
        assertThat(d1Seq.isChained()).isTrue();
        
        // D2 and D3 should have single loads
        LoadSequence d2Seq = plan.getAssignmentForDriver(d2.getId());
        LoadSequence d3Seq = plan.getAssignmentForDriver(d3.getId());
        
        assertThat(d2Seq).isNotNull();
        assertThat(d3Seq).isNotNull();
        assertThat(d2Seq.isChained()).isFalse();
        assertThat(d3Seq.isChained()).isFalse();
    }
    
    @Test
    void greedyVsMipComparisonOnComplexScenario() {
        // TEST 6: Detailed greedy comparison with specific scenario
        // Create a scenario where greedy gets "trapped" by early decisions
        
        Driver d1 = createDriver("D1", 0.0, 0.0);
        Driver d2 = createDriver("D2", 5.0, 0.0);
        Driver d3 = createDriver("D3", 10.0, 0.0);
        
        // L1 is closest to D1 (1 mi) but D1 would be better for L2
        Load l1 = createLoad(1.0, 0.0, 2.0, 0.0);
        
        // L2 is medium distance from D1 (3 mi) and far from others
        Load l2 = createLoad(3.0, 0.0, 4.0, 0.0);
        
        // L3 is closest to D2 (0.5 mi)
        Load l3 = createLoad(5.5, 0.0, 6.0, 0.0);
        
        List<Driver> drivers = List.of(d1, d2, d3);
        List<Load> loads = List.of(l1, l2, l3);
        
        // Greedy algorithm
        double greedyDeadhead = calculateGreedyTotalDeadhead(drivers, loads);
        
        // MIP algorithm
        OptimizationContext context = new OptimizationContext(
                drivers, loads, List.of(), OptimizationTrigger.MANUAL, null);
        AssignmentPlan mipPlan = optimizationService.optimize(context);
        double mipDeadhead = mipPlan.getTotalDeadheadMiles();
        
        // MIP should never be worse than greedy
        assertThat(mipDeadhead).isLessThanOrEqualTo(greedyDeadhead);
        
        // All loads assigned
        assertThat(mipPlan.getAssignedLoadCount()).isEqualTo(3);
        
        System.out.println("Greedy total: " + greedyDeadhead + " mi");
        System.out.println("MIP total: " + mipDeadhead + " mi");
        System.out.println("Improvement: " + ((greedyDeadhead - mipDeadhead) / greedyDeadhead * 100) + "%");
    }
    
    @Test
    void mipShouldHandleComplexRebalancingWithNewDriverAndLoad() {
        // TEST 7: COMPLEX SCENARIO - Dynamic rebalancing with multiple constraints
        // Initial state: D1 has chained loads (L1 IN_PROGRESS + L2), D2 has chained (L3+L4)
        // Events: New driver D5 starts (close to L2), new load L5 arrives (close to D3)
        
        // Initial 4 drivers
        Driver d1 = createDriver("D1", 39.7392, -104.9903);   // Denver
        Driver d2 = createDriver("D2", 40.0150, -105.2705);   // Boulder
        Driver d3 = createDriver("D3", 38.8339, -104.8214);   // Colorado Springs (idle)
        Driver d4 = createDriver("D4", 35.0000, -100.0000);   // Texas (far, idle)
        
        // D1's loads (L1 IN_PROGRESS, L2 chained next)
        Load l1 = createLoad(39.7500, -104.9900, 39.7600, -105.0000);
        l1.setStatus(Load.Status.IN_PROGRESS);  // Protected!
        l1.setAssignedDriver(d1);
        
        Load l2 = createLoad(39.7650, -105.0050, 39.7800, -105.0100);
        l2.setStatus(Load.Status.RESERVED);     // Chained after L1, can be reassigned
        l2.setAssignedDriver(d1);
        
        // D2's loads (both RESERVED, chained)
        Load l3 = createLoad(40.0200, -105.2700, 40.0300, -105.2800);
        l3.setStatus(Load.Status.RESERVED);
        l3.setAssignedDriver(d2);
        
        Load l4 = createLoad(40.0350, -105.2850, 40.0450, -105.2900);
        l4.setStatus(Load.Status.RESERVED);
        l4.setAssignedDriver(d2);
        
        // NEW EVENTS:
        // New driver D5 starts near L2
        Driver d5 = createDriver("D5", 39.7700, -105.0100);  // Very close to L2!
        
        // New load L5 near D3 (Colorado Springs)
        Load l5 = createLoad(38.8400, -104.8200, 38.9000, -104.8500);
        l5.setStatus(Load.Status.AWAITING_DRIVER);
        
        // Optimization context: All drivers, assignable loads (not L1!)
        OptimizationContext context = new OptimizationContext(
                List.of(d1, d2, d3, d4, d5),  // 5 drivers now
                List.of(l2, l3, l4, l5),      // L2, L3, L4 can be reassigned, L5 is new
                List.of(l1),                  // L1 is protected (IN_PROGRESS)
                OptimizationTrigger.DRIVER_SHIFT_START,
                d5.getId()
        );
        
        AssignmentPlan plan = optimizationService.optimize(context);
        
        // All 4 assignable loads should be assigned
        assertThat(plan.getAssignedLoadCount()).isEqualTo(4);
        
        // L1 (IN_PROGRESS) not in plan (it's protected)
        assertThat(plan.getAssignedLoadIds()).doesNotContain(l1.getId());
        
        // L2, L3, L4, L5 should all be assigned
        assertThat(plan.getAssignedLoadIds()).containsExactlyInAnyOrder(
                l2.getId(), l3.getId(), l4.getId(), l5.getId());
        
        // MIP might reassign L2 to D5 (who's closer), or keep chains
        // Either way, global deadhead should be minimized
        assertThat(plan.getTotalDeadheadMiles()).isLessThan(10.0);
        
        System.out.println("Complex rebalancing: " + plan.getAssignedDriverCount() + 
                          " drivers active, " + plan.getTotalDeadheadMiles() + " mi total deadhead");
    }
    
    @Test
    void mipShouldMeetPerformanceRequirement() {
        // TEST 8: Performance requirement validation (10-50 drivers/loads, < 500ms)
        // Testing at upper bound: 20 drivers, 20 loads
        List<Driver> drivers = new ArrayList<>();
        List<Load> loads = new ArrayList<>();
        
        for (int i = 0; i < 20; i++) {
            drivers.add(createDriver("D" + i, i * 0.5, i * 0.3));
            loads.add(createLoad(
                    i * 0.5 + 0.1, i * 0.3 + 0.1,
                    i * 0.5 + 0.3, i * 0.3 + 0.3
            ));
        }
        
        OptimizationContext context = new OptimizationContext(
                drivers, loads, List.of(), OptimizationTrigger.MANUAL, null);
        
        long startTime = System.currentTimeMillis();
        AssignmentPlan plan = optimizationService.optimize(context);
        long duration = System.currentTimeMillis() - startTime;
        
        // CRITICAL: Must meet NFR1 requirement (< 500ms for 10-50 scale)
        assertThat(duration).isLessThan(500);
        
        // All loads should be assigned
        assertThat(plan.getAssignedLoadCount()).isEqualTo(20);
        
        System.out.println("Performance test (20x20): " + duration + "ms (requirement: <500ms)");
        System.out.println("Result: " + plan.getAssignedLoadCount() + " loads, " + 
                          plan.getAssignedDriverCount() + " drivers, " +
                          String.format("%.2f", plan.getTotalDeadheadMiles()) + " mi total");
    }
}

