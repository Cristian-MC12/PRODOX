package com.prodox.controller;

import com.prodox.dto.CreateIndicatorRequest;
import com.prodox.dto.GenerateIndicatorsRequest;
import com.prodox.dto.IndicatorDto;
import com.prodox.dto.RejectIndicatorRequest;
import com.prodox.service.IndicatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/indicators")
@RequiredArgsConstructor
public class IndicatorController {

    private final IndicatorService indicatorService;

    /** GET /api/indicators */
    @GetMapping
    public ResponseEntity<List<IndicatorDto>> list() {
        return ResponseEntity.ok(indicatorService.listAll());
    }

    /** POST /api/indicators — crear manualmente */
    @PostMapping
    public ResponseEntity<IndicatorDto> create(@Valid @RequestBody CreateIndicatorRequest request) {
        return ResponseEntity.ok(indicatorService.create(request));
    }

    /** POST /api/indicators/generate — RF07: Copiloto genera métricas automáticamente */
    @PostMapping("/generate")
    public ResponseEntity<List<IndicatorDto>> generate(@Valid @RequestBody GenerateIndicatorsRequest request) {
        return ResponseEntity.ok(indicatorService.generateForFactor(request));
    }

    /** PATCH /api/indicators/{id}/approve — RF09 */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<IndicatorDto> approve(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(indicatorService.approve(id, auth.getName()));
    }

    /** PATCH /api/indicators/{id}/reject — RF11 */
    @PatchMapping("/{id}/reject")
    public ResponseEntity<IndicatorDto> reject(@PathVariable UUID id,
                                                @Valid @RequestBody RejectIndicatorRequest request,
                                                Authentication auth) {
        return ResponseEntity.ok(indicatorService.reject(id, auth.getName(), request));
    }
}
