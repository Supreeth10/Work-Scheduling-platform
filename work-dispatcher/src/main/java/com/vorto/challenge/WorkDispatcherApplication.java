package com.vorto.challenge;

import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.repository.DriverRepository;
import com.vorto.challenge.repository.LoadRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.vorto.challenge.JtsGeo.point;

@SpringBootApplication
public class WorkDispatcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkDispatcherApplication.class, args);
    }

//    @Bean
//    public CommandLineRunner commandLineRunner(DriverRepository driverRepository) {
//        return args -> {
//			GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
//            Driver driver = new Driver();
//            driver.setId(UUID.fromString("4af608e6-336c-4aad-b0e5-7351c6720284"));
//            driver.setName("Parth");
//			driver.setOnShift(false);
//			Point denver = gf.createPoint(new Coordinate(-104.9903, 39.7392));
//			driver.setCurrentLocation(denver);
//            driverRepository.save(driver);
//        };
//    }

    @Bean
    public CommandLineRunner commandLineRunner(LoadRepository loadRepository) {
        return args -> {
            // Seed only once: if any loads exist, skip
            if (loadRepository.count() > 0) {
                System.out.println("Loads already present; skipping seed.");
                return;
            }

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
        };
    }
}
