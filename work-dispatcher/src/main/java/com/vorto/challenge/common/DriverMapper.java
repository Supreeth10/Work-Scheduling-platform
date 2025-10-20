package com.vorto.challenge.common;

import com.vorto.challenge.DTO.DriverDto;
import com.vorto.challenge.DTO.LocationDto;
import com.vorto.challenge.model.Driver;
import org.locationtech.jts.geom.Point;

import static com.vorto.challenge.common.JtsGeo.toLatLng;

public final class DriverMapper {
    private DriverMapper() {}
    public static DriverDto toDto(Driver d) {
//        Point p = d.getCurrentLocation();
//        Double lat = (p == null) ? null : p.getY(); // y = lat
//        Double lon = (p == null) ? null : p.getX(); // x = lon
//        return new DriverDto(d.getId(), d.getName(), d.isOnShift(), lat, lon);
        Point point = d.getCurrentLocation();
        LocationDto locationDto = (point == null) ? null : toLatLng(point);
        return new DriverDto(d.getId(), d.getName(), d.isOnShift(), locationDto);
    }
}
