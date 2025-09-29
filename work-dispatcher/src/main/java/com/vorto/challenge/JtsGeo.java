package com.vorto.challenge;


import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class JtsGeo {
    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private JtsGeo() {}

    /** Create SRID=4326 Point from (lat, lon). Internally uses (lon, lat) order. */
    public static Point point(double latitude, double longitude) {
        Point p = GF.createPoint(new Coordinate(longitude, latitude)); // (x=lon, y=lat)
        p.setSRID(4326);
        return p;
    }
}