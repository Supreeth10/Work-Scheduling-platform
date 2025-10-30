package com.vorto.challenge.optimization;

import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

/**
 * Utility for calculating distances between geographic points.
 * Uses the Haversine formula for great-circle distance on a spherical Earth.
 */
@Component
public class DistanceCalculator {
    
    /**
     * Earth's radius in miles (mean radius)
     */
    private static final double EARTH_RADIUS_MILES = 3958.8;
    
    /**
     * Calculate the great-circle distance between two points using the Haversine formula.
     * 
     * @param p1 First point (lat/lng where x=longitude, y=latitude)
     * @param p2 Second point (lat/lng where x=longitude, y=latitude)
     * @return Distance in miles
     * @throws IllegalArgumentException if either point is null
     */
    public double calculateDistance(Point p1, Point p2) {
        if (p1 == null || p2 == null) {
            throw new IllegalArgumentException("Points cannot be null");
        }
        
        double lat1 = Math.toRadians(p1.getY());
        double lat2 = Math.toRadians(p2.getY());
        double lon1 = Math.toRadians(p1.getX());
        double lon2 = Math.toRadians(p2.getX());
        
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        
        // Haversine formula
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(lat1) * Math.cos(lat2)
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_MILES * c;
    }
    
    /**
     * Calculate the total deadhead (empty travel) distance for a driver-load sequence.
     * Deadhead includes:
     * - Driver's current location to first load's pickup
     * - If chained: first load's dropoff to second load's pickup
     * 
     * @param driverLocation Driver's current location
     * @param firstLoadPickup First load's pickup location
     * @param firstLoadDropoff First load's dropoff location (null if single load)
     * @param secondLoadPickup Second load's pickup location (null if single load)
     * @return Total deadhead distance in miles
     * @throws IllegalArgumentException if driverLocation or firstLoadPickup is null
     */
    public double calculateDeadheadDistance(
            Point driverLocation,
            Point firstLoadPickup,
            Point firstLoadDropoff,
            Point secondLoadPickup) {
        
        if (driverLocation == null || firstLoadPickup == null) {
            throw new IllegalArgumentException("Driver location and first load pickup cannot be null");
        }
        
        // Deadhead to first pickup
        double totalDeadhead = calculateDistance(driverLocation, firstLoadPickup);
        
        // If chained: add deadhead from L1 dropoff to L2 pickup
        if (firstLoadDropoff != null && secondLoadPickup != null) {
            totalDeadhead += calculateDistance(firstLoadDropoff, secondLoadPickup);
        }
        
        return totalDeadhead;
    }
}
