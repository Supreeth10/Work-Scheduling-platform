package com.vorto.challenge.common;

import com.vorto.challenge.DTO.LoadAssignmentResponse;
import com.vorto.challenge.DTO.LoadSummaryDto;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import org.locationtech.jts.geom.Point;

import static com.vorto.challenge.common.JtsGeo.toLatLng;

public final class LoadMappers {
    private LoadMappers() {}

    /** Maps a Load entity to the lightweight assignment response DTO. */
    public static LoadAssignmentResponse toAssignmentResponse(Load l) {
        if (l == null) return null;

        Point pickup = l.getPickup();
        Point dropoff = l.getDropoff();

        double pickupLat = pickup != null ? pickup.getY() : Double.NaN;
        double pickupLng = pickup != null ? pickup.getX() : Double.NaN;
        double dropoffLat = dropoff != null ? dropoff.getY() : Double.NaN;
        double dropoffLng = dropoff != null ? dropoff.getX() : Double.NaN;

        return new LoadAssignmentResponse(
                l.getId().toString(),
                pickupLat,
                pickupLng,
                dropoffLat,
                dropoffLng,
                l.getStatus().name(),
                l.getCurrentStop().name()
        );
    }


    public static LoadSummaryDto toLoadSummaryDto(Load l) {
    if (l == null) return null;

    Driver d = l.getAssignedDriver();
    LoadSummaryDto.DriverLite driverLite = (d != null)
            ? new LoadSummaryDto.DriverLite(d.getId(), d.getName())
            : null;

    String status = (l.getStatus() != null) ? l.getStatus().name() : null;
    String currentStop = (l.getCurrentStop() != null) ? l.getCurrentStop().name() : null;

    return new LoadSummaryDto(
            l.getId(),
            status,
            currentStop,
            toLatLng(l.getPickup()),
            toLatLng(l.getDropoff()),
            driverLite
    );
}

}
