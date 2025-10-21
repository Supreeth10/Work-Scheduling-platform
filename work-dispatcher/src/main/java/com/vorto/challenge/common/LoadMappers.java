package com.vorto.challenge.common;

import com.vorto.challenge.DTO.LoadAssignmentResponse;
import com.vorto.challenge.DTO.LoadSummaryDto;
import com.vorto.challenge.DTO.LocationDto;
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
        LocationDto pickupDto = (pickup == null) ? null : toLatLng(pickup);
        LocationDto dropoffDto = (dropoff == null) ? null : toLatLng(dropoff);

        String status = l.getStatus() != null ? l.getStatus().name() : null;
        String next   = l.getCurrentStop() != null ? l.getCurrentStop().name() : null;

        return new LoadAssignmentResponse(
                l.getId().toString(),
                pickupDto,
                dropoffDto,
                status,
                next
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
