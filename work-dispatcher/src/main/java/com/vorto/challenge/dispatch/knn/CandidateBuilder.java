package com.vorto.challenge.dispatch.knn;

import com.vorto.challenge.dispatch.model.Candidates;
import com.vorto.challenge.dispatch.model.DriverSnapshot;
import com.vorto.challenge.dispatch.model.LoadSnapshot;

import java.util.List;

/**
 * Interface for building candidate assignments using K-nearest neighbor approach.
 */
public interface CandidateBuilder {

    /**
     * Build candidate loads for each driver.
     *
     * @param drivers List of driver snapshots
     * @param loads List of load snapshots
     * @return List of candidates for each driver
     */
    List<Candidates> buildCandidatesForDrivers(List<DriverSnapshot> drivers, List<LoadSnapshot> loads);

    /**
     * Build candidate drivers for each load.
     *
     * @param loads List of load snapshots
     * @param drivers List of driver snapshots
     * @return List of candidates for each load
     */
    List<Candidates> buildCandidatesForLoads(List<LoadSnapshot> loads, List<DriverSnapshot> drivers);
}

