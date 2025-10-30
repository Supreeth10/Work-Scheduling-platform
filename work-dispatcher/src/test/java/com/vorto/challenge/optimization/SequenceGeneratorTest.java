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
 * Unit tests for SequenceGenerator.
 */
class SequenceGeneratorTest {
    
    private SequenceGenerator sequenceGenerator;
    private DistanceCalculator distanceCalculator;
    private GeometryFactory geometryFactory;
    
    @BeforeEach
    void setUp() {
        distanceCalculator = new DistanceCalculator();
        sequenceGenerator = new SequenceGenerator(distanceCalculator);
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
    void shouldGenerateEmptySequenceForEachDriver() {
        Driver d1 = createDriver("Driver1", 39.7392, -104.9903);
        Driver d2 = createDriver("Driver2", 38.8339, -104.8214);
        List<Driver> drivers = List.of(d1, d2);
        List<Load> loads = List.of();
        
        List<SequenceGenerator.DriverLoadSequence> sequences = 
                sequenceGenerator.generateAllSequences(drivers, loads);
        
        // Should have 2 empty sequences (one per driver)
        assertThat(sequences).hasSize(2);
        assertThat(sequences).allMatch(SequenceGenerator.DriverLoadSequence::isEmpty);
    }
    
    @Test
    void shouldGenerateCorrectNumberOfSequences() {
        Driver d1 = createDriver("Driver1", 39.7392, -104.9903);
        Load l1 = createLoad(38.8339, -104.8214, 38.7000, -104.8000);
        Load l2 = createLoad(40.0000, -105.0000, 40.5000, -105.5000);
        
        List<Driver> drivers = List.of(d1);
        List<Load> loads = List.of(l1, l2);
        
        List<SequenceGenerator.DriverLoadSequence> sequences = 
                sequenceGenerator.generateAllSequences(drivers, loads);
        
        // For 1 driver, 2 loads:
        // 1 empty + 2 single + 2 chained (l1→l2, l2→l1) = 5 total
        assertThat(sequences).hasSize(5);
        
        long emptyCount = sequences.stream()
                .filter(SequenceGenerator.DriverLoadSequence::isEmpty).count();
        long singleCount = sequences.stream()
                .filter(s -> !s.isEmpty() && !s.isChained()).count();
        long chainedCount = sequences.stream()
                .filter(SequenceGenerator.DriverLoadSequence::isChained).count();
        
        assertThat(emptyCount).isEqualTo(1);
        assertThat(singleCount).isEqualTo(2);
        assertThat(chainedCount).isEqualTo(2);
    }
    
    @Test
    void shouldCalculateCorrectDeadheadForSingleLoad() {
        Driver d1 = createDriver("Driver1", 39.7392, -104.9903); // Denver
        Load l1 = createLoad(38.8339, -104.8214, 38.7000, -104.8000); // Colorado Springs
        
        List<Driver> drivers = List.of(d1);
        List<Load> loads = List.of(l1);
        
        List<SequenceGenerator.DriverLoadSequence> sequences = 
                sequenceGenerator.generateAllSequences(drivers, loads);
        
        // Find the single-load sequence
        SequenceGenerator.DriverLoadSequence singleSeq = sequences.stream()
                .filter(s -> !s.isEmpty() && !s.isChained())
                .findFirst()
                .orElseThrow();
        
        // Deadhead should be approximately 63 miles
        assertThat(singleSeq.getDeadheadMiles()).isBetween(62.0, 65.0);
    }
    
    @Test
    void shouldCalculateCorrectDeadheadForChainedLoads() {
        Driver d1 = createDriver("Driver1", 39.7392, -104.9903); // Denver
        Load l1 = createLoad(39.6000, -105.0000, 39.5500, -105.1000);
        Load l2 = createLoad(39.5400, -105.1100, 39.4000, -105.2000);
        
        List<Driver> drivers = List.of(d1);
        List<Load> loads = List.of(l1, l2);
        
        List<SequenceGenerator.DriverLoadSequence> sequences = 
                sequenceGenerator.generateAllSequences(drivers, loads);
        
        // Find chained sequence d1→l1→l2
        SequenceGenerator.DriverLoadSequence chainedSeq = sequences.stream()
                .filter(s -> s.isChained() && s.getLoads().get(0).getId().equals(l1.getId()))
                .findFirst()
                .orElseThrow();
        
        // Deadhead = driver→L1 pickup + L1 dropoff→L2 pickup
        // All near Denver, should be < 20 miles
        assertThat(chainedSeq.getDeadheadMiles()).isLessThan(20.0);
    }
    
    @Test
    void shouldGenerateSequencesForMultipleDrivers() {
        Driver d1 = createDriver("Driver1", 39.7392, -104.9903);
        Driver d2 = createDriver("Driver2", 38.8339, -104.8214);
        Load l1 = createLoad(40.0000, -105.0000, 40.5000, -105.5000);
        
        List<Driver> drivers = List.of(d1, d2);
        List<Load> loads = List.of(l1);
        
        List<SequenceGenerator.DriverLoadSequence> sequences = 
                sequenceGenerator.generateAllSequences(drivers, loads);
        
        // For each driver: 1 empty + 1 single = 2 sequences per driver
        // Total: 4 sequences
        assertThat(sequences).hasSize(4);
        
        // Verify each driver has sequences
        long d1Sequences = sequences.stream()
                .filter(s -> s.getDriver().getId().equals(d1.getId()))
                .count();
        long d2Sequences = sequences.stream()
                .filter(s -> s.getDriver().getId().equals(d2.getId()))
                .count();
        
        assertThat(d1Sequences).isEqualTo(2);
        assertThat(d2Sequences).isEqualTo(2);
    }
    
    @Test
    void shouldGenerateAllPermutationsForChainedSequences() {
        Driver d1 = createDriver("Driver1", 39.7392, -104.9903);
        Load l1 = createLoad(40.0, -105.0, 40.5, -105.5);
        Load l2 = createLoad(39.5, -104.5, 39.0, -104.0);
        Load l3 = createLoad(38.5, -104.5, 38.0, -104.0);
        
        List<Driver> drivers = List.of(d1);
        List<Load> loads = List.of(l1, l2, l3);
        
        List<SequenceGenerator.DriverLoadSequence> sequences = 
                sequenceGenerator.generateAllSequences(drivers, loads);
        
        // Expected sequences:
        // 1 empty
        // 3 single (d1→l1, d1→l2, d1→l3)
        // 6 chained (l1→l2, l1→l3, l2→l1, l2→l3, l3→l1, l3→l2)
        // Total: 10
        assertThat(sequences).hasSize(10);
        
        long chainedCount = sequences.stream()
                .filter(SequenceGenerator.DriverLoadSequence::isChained)
                .count();
        assertThat(chainedCount).isEqualTo(6);
    }
    
    @Test
    void shouldConvertToLoadSequenceDTO() {
        Driver d1 = createDriver("Driver1", 39.7392, -104.9903);
        Load l1 = createLoad(40.0, -105.0, 40.5, -105.5);
        
        List<Driver> drivers = List.of(d1);
        List<Load> loads = List.of(l1);
        
        List<SequenceGenerator.DriverLoadSequence> sequences = 
                sequenceGenerator.generateAllSequences(drivers, loads);
        
        SequenceGenerator.DriverLoadSequence singleSeq = sequences.stream()
                .filter(s -> !s.isEmpty() && !s.isChained())
                .findFirst()
                .orElseThrow();
        
        LoadSequence dto = singleSeq.toLoadSequence();
        
        assertThat(dto.getDriverId()).isEqualTo(d1.getId());
        assertThat(dto.getLoadIds()).containsExactly(l1.getId());
        assertThat(dto.getDeadheadMiles()).isEqualTo(singleSeq.getDeadheadMiles());
    }
    
    @Test
    void shouldIdentifyIfSequenceContainsLoad() {
        Driver d1 = createDriver("Driver1", 39.7392, -104.9903);
        Load l1 = createLoad(40.0, -105.0, 40.5, -105.5);
        Load l2 = createLoad(39.5, -104.5, 39.0, -104.0);
        Load l3 = createLoad(38.0, -104.0, 37.5, -103.5);
        
        List<Driver> drivers = List.of(d1);
        List<Load> loads = List.of(l1, l2);
        
        List<SequenceGenerator.DriverLoadSequence> sequences = 
                sequenceGenerator.generateAllSequences(drivers, loads);
        
        // Find chained sequence d1→l1→l2
        SequenceGenerator.DriverLoadSequence chainedSeq = sequences.stream()
                .filter(s -> s.isChained() 
                        && s.getLoads().get(0).getId().equals(l1.getId())
                        && s.getLoads().get(1).getId().equals(l2.getId()))
                .findFirst()
                .orElseThrow();
        
        assertThat(chainedSeq.containsLoad(l1)).isTrue();
        assertThat(chainedSeq.containsLoad(l2)).isTrue();
        assertThat(chainedSeq.containsLoad(l3)).isFalse();
    }
    
    @Test
    void shouldHandleNoDrivers() {
        List<Driver> drivers = List.of();
        List<Load> loads = List.of(createLoad(40.0, -105.0, 40.5, -105.5));
        
        List<SequenceGenerator.DriverLoadSequence> sequences = 
                sequenceGenerator.generateAllSequences(drivers, loads);
        
        assertThat(sequences).isEmpty();
    }
    
    @Test
    void shouldHandleNoLoads() {
        Driver d1 = createDriver("Driver1", 39.7392, -104.9903);
        List<Driver> drivers = List.of(d1);
        List<Load> loads = List.of();
        
        List<SequenceGenerator.DriverLoadSequence> sequences = 
                sequenceGenerator.generateAllSequences(drivers, loads);
        
        // Should only have empty sequence for d1
        assertThat(sequences).hasSize(1);
        assertThat(sequences.get(0).isEmpty()).isTrue();
    }
    
    @Test
    void shouldGenerateUniqueIdsForAllSequences() {
        Driver d1 = createDriver("Driver1", 39.7392, -104.9903);
        Load l1 = createLoad(40.0, -105.0, 40.5, -105.5);
        Load l2 = createLoad(39.5, -104.5, 39.0, -104.0);
        
        List<Driver> drivers = List.of(d1);
        List<Load> loads = List.of(l1, l2);
        
        List<SequenceGenerator.DriverLoadSequence> sequences = 
                sequenceGenerator.generateAllSequences(drivers, loads);
        
        // All sequence IDs should be unique
        long uniqueIds = sequences.stream()
                .map(SequenceGenerator.DriverLoadSequence::getId)
                .distinct()
                .count();
        
        assertThat(uniqueIds).isEqualTo(sequences.size());
    }
}

