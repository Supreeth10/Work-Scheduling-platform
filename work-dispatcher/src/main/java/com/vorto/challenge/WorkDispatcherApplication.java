package com.vorto.challenge;

import com.vorto.challenge.model.Driver;
import com.vorto.challenge.repository.DriverRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.UUID;

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

//    @Bean
//    public CommandLineRunner seed(org.springframework.jdbc.core.JdbcTemplate jdbc) {
//        return args -> {
//            jdbc.update("""
//      INSERT INTO drivers (id, name, current_location, on_shift)
//      VALUES (? , ?, ST_SetSRID(ST_MakePoint(?, ?),4326)::geography, ?)
//      ON CONFLICT (id) DO UPDATE
//        SET name = EXCLUDED.name,
//            current_location = EXCLUDED.current_location,
//            on_shift = EXCLUDED.on_shift
//      """,
//                    java.util.UUID.fromString("4af608e6-336c-4aad-b0e5-7351c6720285"),
//                    "Shivam", -104.9903, 39.7392, false
//            );
//        };
//    }
}
