package com.mpdia.controller;

import com.mpdia.dto.FactorDto;
import com.mpdia.dto.SelectFactorRequest;
import com.mpdia.dto.SprintSelectionDto;
import com.mpdia.service.FactorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/factors")
@RequiredArgsConstructor
public class FactorController {

    private final FactorService factorService;

    /** GET /api/factors — lista todos los factores */
    @GetMapping
    public ResponseEntity<List<FactorDto>> list() {
        return ResponseEntity.ok(factorService.listFactors());
    }

    /** GET /api/factors/selections?sprintName=Sprint+Actual */
    @GetMapping("/selections")
    public ResponseEntity<List<SprintSelectionDto>> selections(
            @RequestParam(defaultValue = "Sprint Actual") String sprintName) {
        return ResponseEntity.ok(factorService.listSelections(sprintName));
    }

    /** POST /api/factors/selections — seleccionar factor para sprint */
    @PostMapping("/selections")
    public ResponseEntity<Void> select(@Valid @RequestBody SelectFactorRequest request,
                                       Authentication auth) {
        factorService.selectFactor(request, auth.getName());
        return ResponseEntity.ok().build();
    }

    /** DELETE /api/factors/selections/{factorId}?sprintName=Sprint+Actual */
    @DeleteMapping("/selections/{factorId}")
    public ResponseEntity<Void> unselect(@PathVariable UUID factorId,
                                         @RequestParam(defaultValue = "Sprint Actual") String sprintName) {
        factorService.unselectFactor(factorId, sprintName);
        return ResponseEntity.noContent().build();
    }
}
