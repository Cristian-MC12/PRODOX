// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.ParametrizacionRequest;
import com.mpdia.dto.PropuestaParametrizacionDto;
import com.mpdia.service.ParametrizacionService;
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
     * Genera 3 propuestas de parametrización usando Gemini.
     */
    @PostMapping("/propuestas")
    public ResponseEntity<List<PropuestaParametrizacionDto>> propuestas(
            @RequestBody ParametrizacionRequest request) {
        return ResponseEntity.ok(parametrizacionService.generarPropuestas(request));
    }
}
