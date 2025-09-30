package com.vorto.challenge.service;

import com.vorto.challenge.DTO.LoadSummaryDto;
import com.vorto.challenge.model.Load;

import java.util.List;
import java.util.UUID;

public interface LoadService {
    /**
     * Fetch all loads, optionally filtered by status.
     * @param statusOpt null to fetch all
     */
    List<LoadSummaryDto> getAll(Load.Status statusOpt);

    /**
     * Fetch a single load by id or throw NotFoundException.
     */
    LoadSummaryDto getOne(UUID id);
}
