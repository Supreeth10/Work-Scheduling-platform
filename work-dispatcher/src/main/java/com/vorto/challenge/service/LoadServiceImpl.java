package com.vorto.challenge.service;

import com.vorto.challenge.DTO.LoadSummaryDto;
import com.vorto.challenge.DTO.CreateLoadRequest;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.repository.LoadRepository;
import jakarta.transaction.Transactional;
import org.apache.coyote.BadRequestException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class LoadServiceImpl implements LoadService {

    private final LoadRepository loadRepository;
    private final GeometryFactory geometryFactory;
    private final AssignmentService assignmentService;
    private static final Logger log = LoggerFactory.getLogger(LoadServiceImpl.class);

    public LoadServiceImpl(LoadRepository loadRepository,AssignmentService assignmentService) {
        this.loadRepository = loadRepository;
        this.assignmentService = assignmentService;
        this.geometryFactory = new GeometryFactory();
    }

    @Override
    public List<LoadSummaryDto> getAll(Load.Status statusOpt){
        List<Load> loads = (statusOpt == null)
                ? loadRepository.findAll()
                : loadRepository.findAllByStatus(statusOpt);

        return loads.stream().map(this::toDto).toList();
    }
    public LoadSummaryDto getOne(UUID id) {
        Load load = loadRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Load %s not found".formatted(id)));
        return toDto(load);
    }
    @Override
    @Transactional
    public LoadSummaryDto create(CreateLoadRequest req) throws BadRequestException {
        validate(req);

        Point pickup = point(req.pickup().lat(), req.pickup().lng());
        Point dropoff = point(req.dropoff().lat(), req.dropoff().lng());

        Load load = new Load();
        load.setPickup(pickup);
        load.setDropoff(dropoff);
        load.setStatus(Load.Status.AWAITING_DRIVER);
        load.setCurrentStop(Load.StopKind.PICKUP);
        // assignedDriver/assignedShift/reservationExpiresAt remain null

        // Persist first
        Load saved = loadRepository.save(load);

        // ---- defensive auto-assign: don't fail the request if this throws ----
        try {
            assignmentService.tryAssignNewlyCreatedLoad(saved.getId());
        } catch (Exception e) {
            log.warn("Auto-assign after create failed for load {}", saved.getId(), e);
            // swallow so the client still gets 201 + created load
        }

        // Re-read to reflect any assignment that may have happened
        Load refreshed = loadRepository.findById(saved.getId()).orElse(saved);
        return toDto(refreshed);
    }


    private LoadSummaryDto toDto(Load l) {
        LoadSummaryDto.DriverLite driverLite = null;
        Driver d = l.getAssignedDriver();
        if (d != null) {
            driverLite = new LoadSummaryDto.DriverLite(d.getId(), d.getName());
        }
        return new LoadSummaryDto(
                l.getId(),
                l.getStatus().name(),
                l.getCurrentStop().name(),
                toLatLng(l.getPickup()),
                toLatLng(l.getDropoff()),
                driverLite
        );
    }

    private static LoadSummaryDto.LatLng toLatLng(Point p) {
        // JTS Point: X = lng, Y = lat
        return new LoadSummaryDto.LatLng(p.getY(), p.getX());
    }

    private void validate(CreateLoadRequest req) throws BadRequestException {
        if (req == null || req.pickup() == null || req.dropoff() == null) {
            throw new BadRequestException("pickup and dropoff are required");
        }
        checkLatLng("pickup", req.pickup().lat(), req.pickup().lng());
        checkLatLng("dropoff", req.dropoff().lat(), req.dropoff().lng());
    }

    private void checkLatLng(String label, Double lat, Double lng) throws BadRequestException {
        if (lat == null || lng == null) throw new BadRequestException(label + " lat/lng are required");
        if (lat < -90 || lat > 90)   throw new BadRequestException(label + ".lat must be between -90 and 90");
        if (lng < -180 || lng > 180) throw new BadRequestException(label + ".lng must be between -180 and 180");
    }

    private Point point(double lat, double lng) {
        // JTS uses (x=lng, y=lat)
        Point p = geometryFactory.createPoint(new Coordinate(lng, lat));
        p.setSRID(4326);
        return p;
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }
}
