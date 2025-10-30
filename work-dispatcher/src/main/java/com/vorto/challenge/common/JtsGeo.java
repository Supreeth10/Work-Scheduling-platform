package com.vorto.challenge.common;


import com.vorto.challenge.DTO.LocationDto;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class JtsGeo {
    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double EARTH_RADIUS_MILES = 3958.8;
    
    private JtsGeo() {}

    /** Create SRID=4326 Point from (lat, lon). Internally uses (lon, lat) order. */
    public static Point point(double latitude, double longitude) {
        Point p = GF.createPoint(new Coordinate(longitude, latitude)); // (x=lon, y=lat)
        p.setSRID(4326);
        return p;
    }
    
    public static LocationDto toLatLng(Point p) {
        // JTS Point: X = lng, Y = lat
        return (p == null) ? null : new LocationDto(p.getY(), p.getX());
    }

    /**
     * Calculate great-circle distance between two points using Haversine formula.
     *
     * @param lat1 Latitude of first point in degrees
     * @param lng1 Longitude of first point in degrees
     * @param lat2 Latitude of second point in degrees
     * @param lng2 Longitude of second point in degrees
     * @return Distance in miles
     */
    public static double distanceMiles(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_MILES * c;
    }

    /**
     * Calculate great-circle distance between two JTS Points.
     *
     * @param p1 First point (must not be null)
     * @param p2 Second point (must not be null)
     * @return Distance in miles
     */
    public static double distanceMiles(Point p1, Point p2) {
        // JTS Point: X = lng, Y = lat
        return distanceMiles(p1.getY(), p1.getX(), p2.getY(), p2.getX());
    }
}