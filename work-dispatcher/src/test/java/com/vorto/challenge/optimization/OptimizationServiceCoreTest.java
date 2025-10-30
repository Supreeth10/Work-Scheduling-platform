package com.vorto.challenge.optimization;

import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for OptimizationService with full MIP optimization.
 * Verifies MIP finds optimal assignments and handles various scenarios.
 */
class OptimizationServiceCoreTest {
    
    private OptimizationService optimizationService;
    private GeometryFactory geometryFactory;
    
    @BeforeEach
    void setUp() {
        DistanceCalculator distanceCalculator = new DistanceCalculator();
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
    
    @Test
    void shouldReturnEmptyPlanWhenNoDrivers() {
        List<Driver> drivers = List.of();
        List<Load> loads = List.of(createLoad(40.0, -105.0, 40.5, -105.5));
        
        OptimizationContext context = new OptimizationContext(
                drivers, loads, List.of(), OptimizationTrigger.MANUAL, null);
        
        AssignmentPlan plan = optimizationService.optimize(context);
        
        assertThat(plan.isEmpty()).isTrue();
        assertThat(plan.getAssignedDriverCount()).isEqualTo(0);
        assertThat(plan.getAssignedLoadCount()).isEqualTo(0);
    }
    
    @Test
    void shouldReturnEmptyPlanWhenNoLoads() {
        List<Driver> drivers = List.of(createDriver("D1", 39.7392, -104.9903));
        List<Load> loads = List.of();
        
        OptimizationContext context = new OptimizationContext(
                drivers, loads, List.of(), OptimizationTrigger.MANUAL, null);
        
        AssignmentPlan plan = optimizationService.optimize(context);
        
        assertThat(plan.isEmpty()).isTrue();
        assertThat(plan.getAssignedDriverCount()).isEqualTo(0);
        assertThat(plan.getAssignedLoadCount()).isEqualTo(0);
    }
    
    @Test
    void shouldInitializeWithoutErrors() {
        // Verify the service can be created and OR-Tools loads successfully
        assertThat(optimizationService).isNotNull();
        
        // Verify we can call optimize without exceptions
        List<Driver> drivers = List.of(createDriver("D1", 0.0, 0.0));
        List<Load> loads = List.of(createLoad(1.0, 1.0, 2.0, 2.0));
        
        OptimizationContext context = new OptimizationContext(
                drivers, loads, List.of(), OptimizationTrigger.MANUAL, null);
        
        // Should not throw exception and should return valid assignment
        AssignmentPlan plan = optimizationService.optimize(context);
        
        assertThat(plan).isNotNull();
        assertThat(plan.getAssignedLoadCount()).isEqualTo(1);
    }
    
    @Test
    void shouldHandleMultipleDriversAndLoads() {
        List<Driver> drivers = List.of(
                createDriver("D1", 0.0, 0.0),
                createDriver("D2", 5.0, 5.0)
        );
        List<Load> loads = List.of(
                createLoad(1.0, 1.0, 2.0, 2.0),
                createLoad(6.0, 6.0, 7.0, 7.0)
        );
        
        OptimizationContext context = new OptimizationContext(
                drivers, loads, List.of(), OptimizationTrigger.LOAD_CREATED, UUID.randomUUID());
        
        // Should handle input without errors
        AssignmentPlan plan = optimizationService.optimize(context);
        
        assertThat(plan).isNotNull();
        assertThat(plan.getAssignedLoadCount()).isEqualTo(2);
    }
    
    @Test
    void shouldAssignSingleLoadToSingleDriver() {
        Driver d1 = createDriver("D1", 39.7392, -104.9903);
        Load l1 = createLoad(38.8339, -104.8214, 38.7000, -104.8000);
        
        OptimizationContext context = new OptimizationContext(
                List.of(d1), List.of(l1), List.of(), OptimizationTrigger.MANUAL, null);
        
        AssignmentPlan plan = optimizationService.optimize(context);
        
        // Should assign the load to the driver
        assertThat(plan.getAssignedDriverCount()).isEqualTo(1);
        assertThat(plan.getAssignedLoadCount()).isEqualTo(1);
        
        LoadSequence d1Seq = plan.getAssignmentForDriver(d1.getId());
        assertThat(d1Seq).isNotNull();
        assertThat(d1Seq.getLoadIds()).containsExactly(l1.getId());
    }
    
    @Test
    void shouldFindOptimalAssignmentForTwoDriversTwoLoads() {
        // Setup: Each driver is closest to their respective load
        Driver d1 = createDriver("D1", 0.0, 0.0);
        Driver d2 = createDriver("D2", 10.0, 10.0);
        
        Load l1 = createLoad(0.5, 0.5, 1.0, 1.0);     // Close to D1
        Load l2 = createLoad(10.5, 10.5, 11.0, 11.0); // Close to D2
        
        OptimizationContext context = new OptimizationContext(
                List.of(d1, d2), List.of(l1, l2), List.of(), OptimizationTrigger.MANUAL, null);
        
        AssignmentPlan plan = optimizationService.optimize(context);
        
        // Should assign D1→L1 and D2→L2 (optimal pairing)
        assertThat(plan.getAssignedDriverCount()).isEqualTo(2);
        assertThat(plan.getAssignedLoadCount()).isEqualTo(2);
        
        LoadSequence d1Seq = plan.getAssignmentForDriver(d1.getId());
        LoadSequence d2Seq = plan.getAssignmentForDriver(d2.getId());
        
        assertThat(d1Seq.getLoadIds()).containsExactly(l1.getId());
        assertThat(d2Seq.getLoadIds()).containsExactly(l2.getId());
    }
    
    @Test
    void shouldChainLoadsWhenBeneficial() {
        // Setup: Two loads close together, should be chained
        Driver d1 = createDriver("D1", 0.0, 0.0);
        
        Load l1 = createLoad(5.0, 5.0, 5.1, 5.1);
        Load l2 = createLoad(5.15, 5.15, 5.3, 5.3); // Very close to L1 dropoff
        
        OptimizationContext context = new OptimizationContext(
                List.of(d1), List.of(l1, l2), List.of(), OptimizationTrigger.MANUAL, null);
        
        AssignmentPlan plan = optimizationService.optimize(context);
        
        // Should chain both loads
        assertThat(plan.getAssignedLoadCount()).isEqualTo(2);
        
        LoadSequence d1Seq = plan.getAssignmentForDriver(d1.getId());
        assertThat(d1Seq).isNotNull();
        assertThat(d1Seq.isChained()).isTrue();
        assertThat(d1Seq.getLoadIds()).hasSize(2);
    }
    
    @Test
    void shouldHandleMoreLoadsThanDriverCapacity() {
        // Setup: 2 drivers (max 4 loads capacity), 3 loads available
        // This is feasible - can assign all 3 loads
        Driver d1 = createDriver("D1", 0.0, 0.0);
        Driver d2 = createDriver("D2", 10.0, 0.0);
        
        List<Load> loads = List.of(
                createLoad(1.0, 0.0, 2.0, 0.0),
                createLoad(2.5, 0.0, 3.5, 0.0),
                createLoad(11.0, 0.0, 12.0, 0.0)
        );
        
        OptimizationContext context = new OptimizationContext(
                List.of(d1, d2), loads, List.of(), OptimizationTrigger.MANUAL, null);
        
        AssignmentPlan plan = optimizationService.optimize(context);
        
        // Should assign all 3 loads (within capacity of 4)
        assertThat(plan.getAssignedLoadCount()).isEqualTo(3);
        assertThat(plan.getAssignedDriverCount()).isGreaterThan(0);
    }
    
    @Test
    void shouldMinimizeGlobalDeadhead() {
        // COMPLEX TEST: Three drivers and three loads where MIP finds optimal pairing
        // Each driver positioned close to one specific load
        
        Driver d1 = createDriver("D1", 39.7392, -104.9903);   // Denver
        Driver d2 = createDriver("D2", 38.8339, -104.8214);   // Colorado Springs  
        Driver d3 = createDriver("D3", 40.0150, -105.2705);   // Boulder
        
        Load l1 = createLoad(39.7500, -104.9900, 39.8000, -105.0000);  // Near Denver
        Load l2 = createLoad(38.8400, -104.8200, 38.8500, -104.8300);  // Near Colorado Springs
        Load l3 = createLoad(40.0200, -105.2700, 40.0300, -105.2800);  // Near Boulder
        
        OptimizationContext context = new OptimizationContext(
                List.of(d1, d2, d3), List.of(l1, l2, l3), List.of(), 
                OptimizationTrigger.MANUAL, null);
        
        AssignmentPlan plan = optimizationService.optimize(context);
        
        // All loads should be assigned
        assertThat(plan.getAssignedLoadCount()).isEqualTo(3);
        assertThat(plan.getAssignedDriverCount()).isEqualTo(3);
        
        // Each driver should get their closest load
        LoadSequence d1Seq = plan.getAssignmentForDriver(d1.getId());
        LoadSequence d2Seq = plan.getAssignmentForDriver(d2.getId());
        LoadSequence d3Seq = plan.getAssignmentForDriver(d3.getId());
        
        assertThat(d1Seq.getLoadIds()).containsExactly(l1.getId());
        assertThat(d2Seq.getLoadIds()).containsExactly(l2.getId());
        assertThat(d3Seq.getLoadIds()).containsExactly(l3.getId());
        
        // Total deadhead should be minimal (each driver very close to their load)
        assertThat(plan.getTotalDeadheadMiles()).isLessThan(5.0);
    }
    
    @Test
    void shouldChainOptimallyWithIdleDrivers() {
        // COMPLEX TEST: 4 drivers, 4 loads where optimal is:
        // - D1 gets 2 chained loads (L1→L2)
        // - D2 gets 2 chained loads (L3→L4)
        // - D3, D4 remain idle (because they're far from all loads)
        
        // Position D1 and D2 near clusters of loads, D3 and D4 far away
        Driver d1 = createDriver("D1", 39.7392, -104.9903);   // Denver
        Driver d2 = createDriver("D2", 40.0150, -105.2705);   // Boulder
        Driver d3 = createDriver("D3", 35.0000, -100.0000);   // Far away (Texas)
        Driver d4 = createDriver("D4", 45.0000, -110.0000);   // Far away (Montana)
        
        // Two loads clustered near Denver (for D1 to chain)
        Load l1 = createLoad(
                39.7500, -104.9900,  // Pickup near Denver
                39.7600, -105.0000   // Dropoff nearby
        );
        Load l2 = createLoad(
                39.7650, -105.0050,  // Pickup 0.5 mi from L1 dropoff  
                39.7800, -105.0100   // Dropoff
        );
        
        // Two loads clustered near Boulder (for D2 to chain)
        Load l3 = createLoad(
                40.0200, -105.2700,  // Pickup near Boulder
                40.0300, -105.2800   // Dropoff nearby
        );
        Load l4 = createLoad(
                40.0350, -105.2850,  // Pickup 0.5 mi from L3 dropoff
                40.0450, -105.2900   // Dropoff
        );
        
        OptimizationContext context = new OptimizationContext(
                List.of(d1, d2, d3, d4), 
                List.of(l1, l2, l3, l4), 
                List.of(), 
                OptimizationTrigger.MANUAL, 
                null);
        
        AssignmentPlan plan = optimizationService.optimize(context);
        
        // All 4 loads should be assigned
        assertThat(plan.getAssignedLoadCount()).isEqualTo(4);
        
        // Only 2 drivers should be used (D1 and D2)
        assertThat(plan.getAssignedDriverCount()).isEqualTo(2);
        
        // D1 should get L1 and L2 chained
        LoadSequence d1Seq = plan.getAssignmentForDriver(d1.getId());
        assertThat(d1Seq).isNotNull();
        assertThat(d1Seq.isChained()).isTrue();
        assertThat(d1Seq.getLoadIds()).containsExactlyInAnyOrder(l1.getId(), l2.getId());
        
        // D2 should get L3 and L4 chained
        LoadSequence d2Seq = plan.getAssignmentForDriver(d2.getId());
        assertThat(d2Seq).isNotNull();
        assertThat(d2Seq.isChained()).isTrue();
        assertThat(d2Seq.getLoadIds()).containsExactlyInAnyOrder(l3.getId(), l4.getId());
        
        // D3 and D4 should be idle (no assignment)
        assertThat(plan.getAssignmentForDriver(d3.getId())).isNull();
        assertThat(plan.getAssignmentForDriver(d4.getId())).isNull();
        
        // Total deadhead should be minimal (all loads near their assigned drivers)
        assertThat(plan.getTotalDeadheadMiles()).isLessThan(5.0);
    }
    
    @Test
    void shouldCompleteWithin500ms() {
        // Performance test: 10 drivers, 10 loads
        List<Driver> drivers = new java.util.ArrayList<>();
        List<Load> loads = new java.util.ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            drivers.add(createDriver("D" + i, i * 1.0, i * 1.0));
            loads.add(createLoad(i * 1.0 + 0.5, i * 1.0 + 0.5, i * 1.0 + 1.0, i * 1.0 + 1.0));
        }
        
        OptimizationContext context = new OptimizationContext(
                drivers, loads, List.of(), OptimizationTrigger.MANUAL, null);
        
        long startTime = System.currentTimeMillis();
        AssignmentPlan plan = optimizationService.optimize(context);
        long duration = System.currentTimeMillis() - startTime;
        
        // Should complete within 500ms (requirement)
        assertThat(duration).isLessThan(500);
        assertThat(plan.getAssignedLoadCount()).isGreaterThan(0);
    }
}

