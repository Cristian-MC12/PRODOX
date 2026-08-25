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
        String observacion,
        /**
         * FASE 16 — fecha de captura explícita (ISO-8601 instant, ej.
         * "2026-08-21T00:00:00Z"), opcional. Si es null, se usa el
         * comportamiento existente (Instant.now()). Permite registrar varias
         * capturas de la misma variable dentro del mismo sprint sin que la
         * fecha real del servidor las sustituya silenciosamente.
         */
        String fechaCaptura,
        /**
         * Revisión de Ejecución — UUID del RegistroValor que se está
         * editando, opcional. Null significa "captura nueva" (comportamiento
         * existente, sin cambios). Cuando viene informado, el backend
         * actualiza SIEMPRE esa misma fila por ID (nunca crea una nueva) y la
         * excluye de la comprobación de duplicados por frecuencia — así
         * cambiar la fecha al editar una captura 'por_sprint' ya no choca
         * contra el propio registro que se está corrigiendo.
         */
        UUID registroId
    ) {}
}
