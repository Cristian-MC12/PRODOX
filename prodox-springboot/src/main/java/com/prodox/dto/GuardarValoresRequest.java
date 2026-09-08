// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request para guardar valores de variables en un sprint.
 * Fase 16.7: Captura dinámica de variables.
 *
 * proyectoId/sprintId nulos hacían fallar VariableDinamicaService con un 500
 * (findById(null) de Spring Data JPA) en vez de un 400 claro — @NotNull
 * corrige eso sin cambiar ningún caso válido.
 */
public record GuardarValoresRequest(
    @NotNull UUID proyectoId,
    @NotNull UUID sprintId,
    @Valid List<ValorVariable> valores
) {
    public record ValorVariable(
        /** Sin este valor la fila no identifica qué variable se está capturando. */
        @NotNull UUID variableId,
        BigDecimal valorNum,
        /** RegistroValor.valorTexto es columnDefinition="TEXT": sin límite en BD, sin @Size. */
        String valorTexto,
        Boolean valorBool,
        /** RegistroValor.observacion es columnDefinition="TEXT": sin límite en BD, sin @Size. */
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
