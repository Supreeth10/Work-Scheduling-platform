package com.vorto.challenge;

import com.vorto.challenge.config.DispatchOptimizerProperties;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.model.Shift;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.LoadRepository;
import com.vorto.challenge.repository.ShiftRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.vorto.challenge.common.JtsGeo.point;

@SpringBootApplication
@EnableConfigurationProperties(DispatchOptimizerProperties.class)
public class WorkDispatcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkDispatcherApplication.class, args);
    }


    @Bean
    public CommandLineRunner commandLineRunner(LoadRepository loadRepository,
                                               DriverRepository driverRepository,
                                               ShiftRepository shiftRepository) {
        return args -> {
            // Seed drivers if none exist
            if (driverRepository.count() == 0) {
                List<Driver> drivers = new ArrayList<>();

                // Driver 1: Alice in Denver
                Driver alice = new Driver();
                alice.setName("Alice");
                alice.setCurrentLocation(point(39.7392, -104.9903));
                alice.setOnShift(true);
                drivers.add(alice);

                // Driver 2: Bob in Boulder
                Driver bob = new Driver();
                bob.setName("Bob");
                bob.setCurrentLocation(point(40.01499, -105.27055));
                bob.setOnShift(true);
                drivers.add(bob);

                // Driver 3: Carol in Phoenix
                Driver carol = new Driver();
                carol.setName("Carol");
                carol.setCurrentLocation(point(33.4484, -112.0740));
                carol.setOnShift(true);
                drivers.add(carol);

                // Driver 4: Dave in Colorado Springs
                Driver dave = new Driver();
                dave.setName("Dave");
                dave.setCurrentLocation(point(38.8339, -104.8214));
                dave.setOnShift(true);
                drivers.add(dave);

                driverRepository.saveAll(drivers);
                System.out.println("Seeded drivers: " + drivers.size());

                // Create active shifts for all drivers
                List<Shift> shifts = new ArrayList<>();
                Instant now = Instant.now();

                for (Driver driver : drivers) {
                    Shift shift = new Shift();
                    shift.setDriver(driver);
                    shift.setStartTime(now);
                    shift.setEndTime(null); // Active shift
                    shift.setStartLocation(driver.getCurrentLocation());
                    shifts.add(shift);
                }

                shiftRepository.saveAll(shifts);
                System.out.println("Seeded active shifts: " + shifts.size());
            } else {
                System.out.println("Drivers already present; skipping driver seed.");
            }

            // Seed loads if none exist
            if (loadRepository.count() == 0) {
                List<Load> loads = new ArrayList<>();

                // L1: Denver -> Colorado Springs
                Load l1 = new Load();
                l1.setPickup(point(39.7392, -104.9903));   // Denver
                l1.setDropoff(point(38.8339, -104.8214));  // Colorado Springs
                l1.setStatus(Load.Status.AWAITING_DRIVER);
                l1.setCurrentStop(Load.StopKind.PICKUP);
                loads.add(l1);

                // L2: Boulder -> Denver
                Load l2 = new Load();
                l2.setPickup(point(40.01499, -105.27055)); // Boulder
                l2.setDropoff(point(39.7392, -104.9903));  // Denver
                l2.setStatus(Load.Status.AWAITING_DRIVER);
                l2.setCurrentStop(Load.StopKind.PICKUP);
                loads.add(l2);

                // L3: Phoenix -> Tucson
                Load l3 = new Load();
                l3.setPickup(point(33.4484, -112.0740));   // Phoenix
                l3.setDropoff(point(32.2226, -110.9747));  // Tucson
                l3.setStatus(Load.Status.AWAITING_DRIVER);
                l3.setCurrentStop(Load.StopKind.PICKUP);
                loads.add(l3);

                // L4: Denver -> Phoenix
                Load l4 = new Load();
                l4.setPickup(point(39.7392, -104.9903));   // Denver
                l4.setDropoff(point(33.4484, -112.0740));  // Phoenix
                l4.setStatus(Load.Status.AWAITING_DRIVER);
                l4.setCurrentStop(Load.StopKind.PICKUP);
                loads.add(l4);

                loadRepository.saveAll(loads);
                System.out.println("Seeded loads: " + loads.size());
            } else {
                System.out.println("Loads already present; skipping load seed.");
            }
        };
    }
}
