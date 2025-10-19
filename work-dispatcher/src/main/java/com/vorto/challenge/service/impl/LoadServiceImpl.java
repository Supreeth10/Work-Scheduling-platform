package com.vorto.challenge.service.impl;

import com.vorto.challenge.DTO.LoadSummaryDto;
import com.vorto.challenge.DTO.CreateLoadRequest;
import com.vorto.challenge.common.LoadMappers;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.repository.LoadRepository;
import com.vorto.challenge.service.AssignmentService;
import com.vorto.challenge.service.LoadService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.vorto.challenge.common.JtsGeo.point;
import static com.vorto.challenge.common.LoadMappers.toLoadSummaryDto;

@Service
public class LoadServiceImpl implements LoadService {

    private final LoadRepository loadRepository;
    private final AssignmentService assignmentService;
    private static final Logger log = LoggerFactory.getLogger(LoadServiceImpl.class);

    public LoadServiceImpl(LoadRepository loadRepository,AssignmentService assignmentService) {
        this.loadRepository = loadRepository;
        this.assignmentService = assignmentService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoadSummaryDto> getAll(Load.Status statusOpt){
        List<Load> loads = (statusOpt == null)
                ? loadRepository.findAll()
                : loadRepository.findAllByStatus(statusOpt);

        return loads.stream().map(LoadMappers::toLoadSummaryDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LoadSummaryDto getOne(UUID id) {
        Load load = loadRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Load not found: " + id));
        return toLoadSummaryDto(load);
    }

    @Override
    @Transactional
    public LoadSummaryDto create(CreateLoadRequest createLoadRequest) {

        Point pickup = point(createLoadRequest.pickup().lat(), createLoadRequest.pickup().lng());
        Point dropoff = point(createLoadRequest.dropoff().lat(), createLoadRequest.dropoff().lng());

        Load load = new Load();
        load.setPickup(pickup);
        load.setDropoff(dropoff);
        load.setStatus(Load.Status.AWAITING_DRIVER);
        load.setCurrentStop(Load.StopKind.PICKUP);
        // assignedDriver/assignedShift/reservationExpiresAt remain null

        // Persist first
        Load saved = loadRepository.save(load);

        // ---- defensive auto-assign: doesn't fail the request if this throws ----
        try {
            assignmentService.tryAssignNewlyCreatedLoad(saved.getId());
        } catch (Exception e) {
            log.warn("Auto-assign after create failed for load {}", saved.getId(), e);
            // swallow so the client still gets 201 + created load
        }

        // Re-read to reflect any assignment that may have happened
        Load refreshed = loadRepository.findById(saved.getId()).orElse(saved);
        return toLoadSummaryDto(refreshed);
    }

}
