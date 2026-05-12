package com.mpdia.controller;

import com.mpdia.dto.ParametrizacionRequest;
import com.mpdia.dto.PropuestaParametrizacionDto;
import com.mpdia.service.ParametrizacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parametrizacion")
@RequiredArgsConstructor
public class ParametrizacionController {

    private final ParametrizacionService parametrizacionService;

    /**
     * POST /api/parametrizacion/propuestas
     * GenAI genera 3 propuestas de parametrización para la métrica indicada.
     */
    @PostMapping("/propuestas")
    public ResponseEntity<List<PropuestaParametrizacionDto>> propuestas(
            @Valid @RequestBody ParametrizacionRequest request) {
        return ResponseEntity.ok(parametrizacionService.generarPropuestas(request));
    }
}
