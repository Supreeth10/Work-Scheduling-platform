package com.vorto.challenge.controller;

import com.vorto.challenge.DTO.LoadSummaryDto;
import com.vorto.challenge.model.Load;
import com.vorto.challenge.service.LoadService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/loads")
public class LoadController {

    private final LoadService service;

    public LoadController(LoadService service) {
        this.service = service;
    }

    @GetMapping
    public List<LoadSummaryDto> getAll(@RequestParam(value = "status", required = false) Load.Status status) {
        return service.getAll(status);
    }

    @GetMapping("/{id}")
    public LoadSummaryDto getOne(@PathVariable UUID id) {
        return service.getOne(id);
    }
}
