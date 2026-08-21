// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request para guardar valores de variables en un sprint.
 * Fase 16.7: Captura dinámica de variables.
 */
public record GuardarValoresRequest(
    UUID proyectoId,
    UUID sprintId,
    List<ValorVariable> valores
) {
    public record ValorVariable(
        UUID variableId,
        BigDecimal valorNum,
        String valorTexto,
        Boolean valorBool,
        String observacion
    ) {}
}
