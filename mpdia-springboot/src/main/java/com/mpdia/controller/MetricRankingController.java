// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.GuardarParametrizacionRequest;
import com.mpdia.dto.MetricParametrizacionDto;
import com.mpdia.dto.RankingMetricaDto;
import com.mpdia.security.JwtUtil;
import com.mpdia.service.MetricRankingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/metric-ranking")
@RequiredArgsConstructor
public class MetricRankingController {

    private final MetricRankingService service;
    private final JwtUtil jwtUtil;

    /** GET /api/metric-ranking/pendientes — parametrizaciones pendientes (solo Scrum Master) */
    @GetMapping("/pendientes")
    public ResponseEntity<List<com.mpdia.dto.MetricParametrizacionDto>> pendientes() {
        return ResponseEntity.ok(service.getPendientes());
    }

    /** POST /api/metric-ranking/verificar — aprobar o rechazar (solo Scrum Master) */
    @PostMapping("/verificar")
    public ResponseEntity<com.mpdia.dto.MetricParametrizacionDto> verificar(
            @jakarta.validation.Valid @RequestBody com.mpdia.dto.VerificarParametrizacionRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {
        String revisadoPor = jwtUtil.getEmail(httpRequest.getHeader("Authorization").substring(7));
        return ResponseEntity.ok(service.verificar(request, revisadoPor));
    }

    /** GET /api/metric-ranking — top 5 métricas más usadas */
    @GetMapping
    public ResponseEntity<List<RankingMetricaDto>> ranking() {
        return ResponseEntity.ok(service.getRanking());
    }

    /** GET /api/metric-ranking/{factorId}/top3 — top 3 parametrizaciones del factor */
    @GetMapping("/{factorId}/top3")
    public ResponseEntity<List<com.mpdia.dto.TopParametrizacionDto>> top3(@PathVariable UUID factorId) {
        return ResponseEntity.ok(service.getTop3(factorId));
    }

    /** GET /api/metric-ranking/{factorId}/base — parametrización base de una métrica */
    @GetMapping("/{factorId}/base")
    public ResponseEntity<MetricParametrizacionDto> base(@PathVariable UUID factorId) {
        return service.getBase(factorId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /** POST /api/metric-ranking/{factorId}/uso — incrementar uso al seleccionar */
    @PostMapping("/{factorId}/uso")
    public ResponseEntity<Void> incrementarUso(@PathVariable UUID factorId) {
        service.incrementarUso(factorId);
        return ResponseEntity.ok().build();
    }

    /** POST /api/metric-ranking/parametrizacion — guardar parametrización */
    @PostMapping("/parametrizacion")
    public ResponseEntity<MetricParametrizacionDto> guardar(
            @Valid @RequestBody GuardarParametrizacionRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {
        String userId    = auth.getName();
        String token     = httpRequest.getHeader("Authorization").substring(7);
        String userEmail = jwtUtil.getEmail(token);
        return ResponseEntity.ok(service.guardar(request, userId, userEmail));
    }
}
