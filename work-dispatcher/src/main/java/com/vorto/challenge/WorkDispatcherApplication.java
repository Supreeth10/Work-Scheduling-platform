package com.vorto.challenge;

import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.model.Shift;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.LoadRepository;
import com.vorto.challenge.repository.ShiftRepository;
import com.vorto.challenge.service.impl.AssignmentServiceImpl;
import com.vorto.challenge.optimization.OptimizationTrigger;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.vorto.challenge.common.JtsGeo.point;

@SpringBootApplication
public class WorkDispatcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkDispatcherApplication.class, args);
    }


    @Bean
    public CommandLineRunner commandLineRunner(
            DriverRepository driverRepository,
            ShiftRepository shiftRepository,
            LoadRepository loadRepository,
            AssignmentServiceImpl assignmentService) {
        return args -> {
            // Seed only once: if any data exists, skip
            if (driverRepository.count() > 0 || loadRepository.count() > 0) {
                System.out.println("Data already present; skipping seed.");
                return;
            }

            System.out.println("=== Starting Data Seeding ===");
            
            // ============ SEED DRIVERS ============
            List<Driver> drivers = new ArrayList<>();
            
            // D1: In Denver (will get Denver loads)
            Driver d1 = new Driver();
            d1.setName("Alice");
            d1.setOnShift(true);
            d1.setCurrentLocation(point(39.7392, -104.9903)); // Denver
            drivers.add(d1);
            
            // D2: In Boulder (will get Boulder load)
            Driver d2 = new Driver();
            d2.setName("Bob");
            d2.setOnShift(true);
            d2.setCurrentLocation(point(40.01499, -105.27055)); // Boulder
            drivers.add(d2);
            
            // D3: In Phoenix (will get Phoenix load)
            Driver d3 = new Driver();
            d3.setName("Charlie");
            d3.setOnShift(true);
            d3.setCurrentLocation(point(33.4484, -112.0740)); // Phoenix
            drivers.add(d3);
            
            driverRepository.saveAll(drivers);
            System.out.println("✓ Seeded drivers: " + drivers.size());
            
            // ============ CREATE SHIFTS ============
            for (Driver driver : drivers) {
                Shift shift = new Shift();
                shift.setDriver(driver);
                shift.setStartTime(Instant.now());
                shift.setStartLocation(driver.getCurrentLocation());
                shiftRepository.save(shift);
            }
            System.out.println("✓ Created shifts for all drivers");
            
            // ============ SEED LOADS ============
            List<Load> loads = new ArrayList<>();

            // L1: Denver -> Colorado Springs (closest to D1 Alice)
            Load l1 = new Load();
            l1.setPickup(point(39.7392, -104.9903));   // Denver
            l1.setDropoff(point(38.8339, -104.8214));  // Colorado Springs
            l1.setStatus(Load.Status.AWAITING_DRIVER);
            l1.setCurrentStop(Load.StopKind.PICKUP);
            loads.add(l1);

            // L2: Boulder -> Denver (closest to D2 Bob - at pickup!)
            Load l2 = new Load();
            l2.setPickup(point(40.01499, -105.27055)); // Boulder
            l2.setDropoff(point(39.7392, -104.9903));  // Denver
            l2.setStatus(Load.Status.AWAITING_DRIVER);
            l2.setCurrentStop(Load.StopKind.PICKUP);
            loads.add(l2);

            // L3: Phoenix -> Tucson (closest to D3 Charlie - at pickup!)
            Load l3 = new Load();
            l3.setPickup(point(33.4484, -112.0740));   // Phoenix
            l3.setDropoff(point(32.2226, -110.9747));  // Tucson
            l3.setStatus(Load.Status.AWAITING_DRIVER);
            l3.setCurrentStop(Load.StopKind.PICKUP);
            loads.add(l3);

            // L4: Near Denver (could chain with L1 for D1)
            Load l4 = new Load();
            l4.setPickup(point(38.9, -104.85));        // Between Denver and Colorado Springs
            l4.setDropoff(point(38.5, -104.7));        // Further south
            l4.setStatus(Load.Status.AWAITING_DRIVER);
            l4.setCurrentStop(Load.StopKind.PICKUP);
            loads.add(l4);

            loadRepository.saveAll(loads);
            System.out.println("✓ Seeded loads: " + loads.size());
            
            // ============ TRIGGER INITIAL OPTIMIZATION ============
            System.out.println("\n=== Triggering Initial Optimization ===");
            try {
                assignmentService.optimizeAndAssign(OptimizationTrigger.MANUAL, null);
                System.out.println("✓ Initial optimization complete!");
                
                // Show assignments
                List<Load> allLoads = loadRepository.findAll();
                System.out.println("\n=== Initial Assignments ===");
                for (Load load : allLoads) {
                    String driverName = load.getAssignedDriver() != null 
                        ? load.getAssignedDriver().getName() 
                        : "UNASSIGNED";
                    System.out.println(String.format("Load at (%.2f, %.2f) → %s [%s]",
                        load.getPickup().getY(), load.getPickup().getX(),
                        driverName, load.getStatus()));
                }
            } catch (Exception e) {
                System.out.println("✗ Optimization failed: " + e.getMessage());
            }
            
            System.out.println("\n=== Seed Complete ===");
            System.out.println("Drivers: " + driverRepository.count() + " (all on-shift)");
            System.out.println("Loads: " + loadRepository.count());
            System.out.println("\nTry these APIs:");
            System.out.println("- GET /api/drivers (list all drivers)");
            System.out.println("- GET /api/loads (see assignments)");
            System.out.println("- GET /api/drivers/{driver-id}/assignment");
        };
    }
}
