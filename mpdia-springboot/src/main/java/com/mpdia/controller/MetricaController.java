// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.MetricaDto;
import com.mpdia.service.MetricaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/metricas")
@RequiredArgsConstructor
public class MetricaController {

    private final MetricaService metricaService;

    /** GET /api/metricas — todas las métricas ordenadas por categoría */
    @GetMapping
    public ResponseEntity<List<MetricaDto>> listar() {
        return ResponseEntity.ok(metricaService.listar());
    }

    /** GET /api/metricas?categoria=Significado */
    @GetMapping(params = "categoria")
    public ResponseEntity<List<MetricaDto>> listarPorCategoria(@RequestParam String categoria) {
        return ResponseEntity.ok(metricaService.listarPorCategoria(categoria));
    }
}
