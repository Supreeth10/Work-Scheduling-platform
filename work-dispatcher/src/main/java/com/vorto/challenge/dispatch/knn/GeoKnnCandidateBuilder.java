package com.vorto.challenge.dispatch.knn;

import com.vorto.challenge.config.DispatchOptimizerProperties;
import com.vorto.challenge.dispatch.model.Candidates;
import com.vorto.challenge.dispatch.model.DriverSnapshot;
import com.vorto.challenge.dispatch.model.LoadSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds candidate assignments using geographic K-nearest neighbor approach.
 */
@Component
public class GeoKnnCandidateBuilder implements CandidateBuilder {
    private static final Logger log = LoggerFactory.getLogger(GeoKnnCandidateBuilder.class);

    private final DispatchOptimizerProperties properties;

    public GeoKnnCandidateBuilder(DispatchOptimizerProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Candidates> buildCandidatesForDrivers(List<DriverSnapshot> drivers, List<LoadSnapshot> loads) {
        int k = properties.getKnnK1();
        log.debug("Building candidates for {} drivers using k1={}", drivers.size(), k);
        // TODO: Implement KNN candidate building for drivers
        return List.of();
    }

    @Override
    public List<Candidates> buildCandidatesForLoads(List<LoadSnapshot> loads, List<DriverSnapshot> drivers) {
        int k = properties.getKnnK2();
        log.debug("Building candidates for {} loads using k2={}", loads.size(), k);
        // TODO: Implement KNN candidate building for loads
        return List.of();
    }
}

