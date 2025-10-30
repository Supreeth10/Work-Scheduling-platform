package com.vorto.challenge.dispatch.knn;

import com.vorto.challenge.common.JtsGeo;
import com.vorto.challenge.config.DispatchOptimizerProperties;
import com.vorto.challenge.dispatch.model.Candidates;
import com.vorto.challenge.dispatch.model.DriverSnapshot;
import com.vorto.challenge.dispatch.model.LoadSnapshot;
import com.vorto.challenge.dispatch.model.Route;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * Build K-nearest load candidates for drivers and loads.
     *
     * @param drivers List of drivers (on shift, no active load)
     * @param loads List of unassigned loads
     * @param k1 Number of nearest loads to find for each driver
     * @param k2 Number of nearest loads to find for each load
     * @return Combined list of candidates
     */
    public List<Candidates> build(List<Driver> drivers, List<Load> loads, int k1, int k2) {
        List<Candidates> allCandidates = new ArrayList<>();
        
        // Build candidates for each driver
        for (Driver driver : drivers) {
            if (driver.getCurrentLocation() == null) {
                log.warn("Driver {} has no current location, skipping", driver.getId());
                continue;
            }
            
            List<LoadDistance> distances = computeDistances(driver.getCurrentLocation(), loads);
            List<UUID> topK = selectTopK(distances, k1);
            List<Route> routes = distances.stream()
                    .filter(ld -> topK.contains(ld.loadId()))
                    .map(ld -> new Route(ld.miles() * 5280, ld.miles() / 60.0 * 3600, ld.miles()))
                    .collect(Collectors.toList());
            
            allCandidates.add(new Candidates(driver.getId(), topK, routes));
        }
        
        // Build candidates for each load (from dropoff to other loads' pickups)
        for (Load firstLoad : loads) {
            Point dropoff = firstLoad.getDropoff();
            if (dropoff == null) {
                log.warn("Load {} has no dropoff location, skipping", firstLoad.getId());
                continue;
            }
            
            // Get all other loads (exclude current load)
            List<Load> otherLoads = loads.stream()
                    .filter(l -> !l.getId().equals(firstLoad.getId()))
                    .collect(Collectors.toList());
            
            List<LoadDistance> distances = computeDistances(dropoff, otherLoads);
            List<UUID> topK = selectTopK(distances, k2);
            List<Route> routes = distances.stream()
                    .filter(ld -> topK.contains(ld.loadId()))
                    .map(ld -> new Route(ld.miles() * 5280, ld.miles() / 60.0 * 3600, ld.miles()))
                    .collect(Collectors.toList());
            
            allCandidates.add(new Candidates(firstLoad.getId(), topK, routes));
        }
        
        log.debug("Built candidates: {} drivers, {} loads, total {} candidate sets",
                drivers.size(), loads.size(), allCandidates.size());
        
        return allCandidates;
    }

    @Override
    public List<Candidates> buildCandidatesForDrivers(List<DriverSnapshot> drivers, List<LoadSnapshot> loads) {
        int k = properties.getKnnK1();
        log.debug("Building candidates for {} drivers using k1={}", drivers.size(), k);
        // TODO: Implement KNN candidate building for drivers using snapshots
        return List.of();
    }

    @Override
    public List<Candidates> buildCandidatesForLoads(List<LoadSnapshot> loads, List<DriverSnapshot> drivers) {
        int k = properties.getKnnK2();
        log.debug("Building candidates for {} loads using k2={}", loads.size(), k);
        // TODO: Implement KNN candidate building for loads using snapshots
        return List.of();
    }

    /**
     * Package-private: Compute distances from a point to all loads' pickup locations.
     *
     * @param from Starting point
     * @param toLoads Target loads
     * @return List of load distances
     */
    List<LoadDistance> computeDistances(Point from, List<Load> toLoads) {
        List<LoadDistance> distances = new ArrayList<>();
        for (Load load : toLoads) {
            Point pickup = load.getPickup();
            if (pickup != null) {
                double miles = JtsGeo.distanceMiles(from, pickup);
                distances.add(new LoadDistance(load.getId(), miles));
            }
        }
        return distances;
    }

    /**
     * Package-private: Select top K loads by distance.
     *
     * @param distances List of load distances
     * @param k Number to select
     * @return List of load IDs (top K)
     */
    List<UUID> selectTopK(List<LoadDistance> distances, int k) {
        return distances.stream()
                .sorted(Comparator.comparingDouble(LoadDistance::miles))
                .limit(k)
                .map(LoadDistance::loadId)
                .collect(Collectors.toList());
    }

    /**
     * Package-private helper record for distance calculations.
     */
    record LoadDistance(UUID loadId, double miles) {}
}

