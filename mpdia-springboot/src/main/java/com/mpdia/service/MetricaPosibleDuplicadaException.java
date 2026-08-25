// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.PosibleDuplicadoDto;

import java.util.List;

/**
 * FASE 23 — se lanza cuando MetricaIAService.crearDesdeConfirmacion() detecta,
 * vía MetricaSimilitudService, una o más métricas ya existentes en el
 * catálogo que probablemente miden el mismo concepto que la propuesta (sin
 * tener el mismo nombre normalizado exacto — ese caso lo sigue cubriendo
 * MetricaDuplicadaEnCatalogoException sin cambios).
 *
 * A diferencia del duplicado exacto, esto NO bloquea la creación: transporta
 * los candidatos para que el controller se lo devuelva al frontend y el
 * Scrum Master decida explícitamente entre reutilizar uno existente, crear
 * la métrica como diferente de todas formas, o cancelar.
 */
public class MetricaPosibleDuplicadaException extends RuntimeException {

    private final List<PosibleDuplicadoDto> candidatos;

    public MetricaPosibleDuplicadaException(String message, List<PosibleDuplicadoDto> candidatos) {
        super(message);
        this.candidatos = candidatos;
    }

    public List<PosibleDuplicadoDto> getCandidatos() {
        return candidatos;
    }
}
