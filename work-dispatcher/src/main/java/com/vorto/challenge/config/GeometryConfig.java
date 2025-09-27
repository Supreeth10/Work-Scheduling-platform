package com.vorto.challenge.config;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeometryConfig {
    @Bean
    public GeometryFactory geometryFactory() {
        // SRID 4326 (WGS84). JTS uses (x=lon, y=lat); we’ll set SRID on Points we create.
        return new GeometryFactory(new PrecisionModel(), 4326);
    }
}
