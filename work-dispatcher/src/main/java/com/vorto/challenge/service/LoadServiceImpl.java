package com.vorto.challenge.service;

import com.vorto.challenge.DTO.LoadSummaryDto;
import com.vorto.challenge.model.Driver;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.repository.LoadRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class LoadServiceImpl implements LoadService {

    private final LoadRepository loadRepository;

    public LoadServiceImpl(LoadRepository loadRepository) {
        this.loadRepository = loadRepository;
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
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }
}
