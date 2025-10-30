package com.vorto.challenge.optimization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for DistanceCalculator using Haversine formula.
 */
class DistanceCalculatorTest {
    
    private DistanceCalculator calculator;
    private GeometryFactory geometryFactory;
    
    @BeforeEach
    void setUp() {
        calculator = new DistanceCalculator();
        geometryFactory = new GeometryFactory();
    }
    
    private Point createPoint(double lat, double lng) {
        return geometryFactory.createPoint(new Coordinate(lng, lat));
    }
    
    @Test
    void shouldCalculateDistanceBetweenIdenticalPoints() {
        Point denver = createPoint(39.7392, -104.9903);
        
        double distance = calculator.calculateDistance(denver, denver);
        
        assertThat(distance).isCloseTo(0.0, within(0.01));
    }
    
    @Test
    void shouldCalculateDistanceBetweenDenverAndColoradoSprings() {
        // Denver, CO
        Point denver = createPoint(39.7392, -104.9903);
        
        // Colorado Springs, CO (approximately 63 miles south)
        Point coloradoSprings = createPoint(38.8339, -104.8214);
        
        double distance = calculator.calculateDistance(denver, coloradoSprings);
        
        // Expected distance is approximately 63 miles (Haversine great-circle distance)
        assertThat(distance).isBetween(62.0, 65.0);
    }
    
    @Test
    void shouldCalculateDistanceBetweenNewYorkAndLosAngeles() {
        // New York, NY
        Point newYork = createPoint(40.7128, -74.0060);
        
        // Los Angeles, CA
        Point losAngeles = createPoint(34.0522, -118.2437);
        
        double distance = calculator.calculateDistance(newYork, losAngeles);
        
        // Expected distance is approximately 2,445 miles
        assertThat(distance).isBetween(2400.0, 2500.0);
    }
    
    @Test
    void shouldCalculateShortDistance() {
        // Two points approximately 1 mile apart
        Point point1 = createPoint(39.7392, -104.9903);
        Point point2 = createPoint(39.7537, -104.9903); // ~1 mile north
        
        double distance = calculator.calculateDistance(point1, point2);
        
        assertThat(distance).isBetween(0.9, 1.1);
    }
    
    @Test
    void shouldBeSymmetric() {
        Point denver = createPoint(39.7392, -104.9903);
        Point coloradoSprings = createPoint(38.8339, -104.8214);
        
        double distance1 = calculator.calculateDistance(denver, coloradoSprings);
        double distance2 = calculator.calculateDistance(coloradoSprings, denver);
        
        assertThat(distance1).isCloseTo(distance2, within(0.001));
    }
    
    @Test
    void shouldThrowExceptionForNullPoint1() {
        Point point = createPoint(39.7392, -104.9903);
        
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculateDistance(null, point);
        });
    }
    
    @Test
    void shouldThrowExceptionForNullPoint2() {
        Point point = createPoint(39.7392, -104.9903);
        
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculateDistance(point, null);
        });
    }
    
    @Test
    void shouldCalculateDeadheadDistanceForSingleLoad() {
        Point driverLocation = createPoint(39.7392, -104.9903); // Denver
        Point loadPickup = createPoint(38.8339, -104.8214);     // Colorado Springs
        
        double deadhead = calculator.calculateDeadheadDistance(
                driverLocation,
                loadPickup,
                null,
                null
        );
        
        // Should be approximately 63 miles
        assertThat(deadhead).isBetween(62.0, 65.0);
    }
    
    @Test
    void shouldCalculateDeadheadDistanceForChainedLoads() {
        Point driverLocation = createPoint(39.7392, -104.9903); // Denver
        Point load1Pickup = createPoint(39.6000, -105.0000);    // ~10 miles away
        Point load1Dropoff = createPoint(39.5500, -105.1000);   // ~5 miles from pickup
        Point load2Pickup = createPoint(39.5400, -105.1100);    // ~1 mile from L1 dropoff
        
        double deadhead = calculator.calculateDeadheadDistance(
                driverLocation,
                load1Pickup,
                load1Dropoff,
                load2Pickup
        );
        
        // Total deadhead = driver→L1 pickup (~10 mi) + L1 dropoff→L2 pickup (~1 mi) ≈ 11 mi
        assertThat(deadhead).isBetween(10.0, 12.0);
    }
    
    @Test
    void shouldIgnoreDropoffIfNoSecondLoad() {
        Point driverLocation = createPoint(39.7392, -104.9903);
        Point load1Pickup = createPoint(38.8339, -104.8214);
        Point load1Dropoff = createPoint(38.7000, -104.8000);
        
        // Second pickup is null, so dropoff should be ignored
        double deadhead = calculator.calculateDeadheadDistance(
                driverLocation,
                load1Pickup,
                load1Dropoff,
                null
        );
        
        // Should only measure driver→L1 pickup
        assertThat(deadhead).isBetween(62.0, 65.0);
    }
    
    @Test
    void shouldThrowExceptionForNullDriverLocation() {
        Point loadPickup = createPoint(38.8339, -104.8214);
        
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculateDeadheadDistance(null, loadPickup, null, null);
        });
    }
    
    @Test
    void shouldThrowExceptionForNullFirstLoadPickup() {
        Point driverLocation = createPoint(39.7392, -104.9903);
        
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculateDeadheadDistance(driverLocation, null, null, null);
        });
    }
    
    @Test
    void shouldHandleEquatorCrossing() {
        // Point in northern hemisphere
        Point north = createPoint(10.0, 0.0);
        
        // Point in southern hemisphere
        Point south = createPoint(-10.0, 0.0);
        
        double distance = calculator.calculateDistance(north, south);
        
        // Approximately 1,380 miles (20 degrees latitude)
        assertThat(distance).isBetween(1350.0, 1410.0);
    }
    
    @Test
    void shouldHandleDateLineCrossing() {
        // Point east of date line
        Point east = createPoint(0.0, 179.0);
        
        // Point west of date line
        Point west = createPoint(0.0, -179.0);
        
        double distance = calculator.calculateDistance(east, west);
        
        // Should calculate shortest path (2 degrees) not longest (358 degrees)
        assertThat(distance).isLessThan(200.0);
    }
}
