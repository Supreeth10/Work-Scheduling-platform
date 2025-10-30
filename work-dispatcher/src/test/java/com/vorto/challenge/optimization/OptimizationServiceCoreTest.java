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
 * Tests for OptimizationService core functionality.
 * Full MIP solving tests will be added after MIP model is implemented.
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
        
        // Should not throw exception (even though MIP not implemented yet)
        AssignmentPlan plan = optimizationService.optimize(context);
        
        assertThat(plan).isNotNull();
        // Will be empty until MIP model is implemented in next task
        assertThat(plan.isEmpty()).isTrue();
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
    }
}

