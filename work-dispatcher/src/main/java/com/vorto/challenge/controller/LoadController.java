package com.vorto.challenge.controller;

import com.vorto.challenge.DTO.CreateLoadRequest;
import com.vorto.challenge.DTO.LoadSummaryDto;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.service.LoadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/loads")
public class LoadController {

    private final LoadService loadService;

    public LoadController(LoadService loadService) {
        this.loadService = loadService;
    }

    @GetMapping
    public List<LoadSummaryDto> getAll(@RequestParam(value = "status", required = false) Load.Status status) {
        return loadService.getAll(status);
    }

    @GetMapping("/{id}")
    public LoadSummaryDto getOne(@PathVariable UUID id) {
        return loadService.getOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoadSummaryDto create(@RequestBody @Valid CreateLoadRequest createLoadRequest) {
        return loadService.create(createLoadRequest);
    }
}
