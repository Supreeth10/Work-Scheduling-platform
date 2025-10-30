package com.vorto.challenge.dispatch.solver;

/**
 * Interface for solving minimum cost flow problems in dispatch optimization.
 */
public interface MinCostFlowSolver {

    /**
     * Solves the min-cost flow problem and returns the solution.
     * Implementation details will vary based on the algorithm used.
     *
     * @return Solution object containing optimal assignments
     */
    Object solve();
}

