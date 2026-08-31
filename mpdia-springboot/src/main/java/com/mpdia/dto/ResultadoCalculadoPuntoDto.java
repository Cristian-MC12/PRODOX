// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un punto de la serie de resultados YA CALCULADOS (ResultadoMetrica, vigente=true)
 * de una métrica, uno por sprint — a diferencia de RegistroPuntoDto (un registro
 * individual crudo), este representa el resultado del EQUIPO para ese sprint,
 * ya reducido/agregado y evaluado con la fórmula aprobada.
 *
 * Solo tiene sentido para frecuenciaCaptura='por_sprint': ResultadoMetrica no
 * tiene hoy granularidad semanal/diaria (solo sprintId), así que para esas
 * frecuencias esta lista viene vacía y la gráfica sigue usando los registros
 * crudos agrupados (ver EvaluacionService.evaluarDetalle).
 */
public record ResultadoCalculadoPuntoDto(
    UUID       resultadoId,
    BigDecimal resultado,
    UUID       sprintId,
    Integer    sprintNumero,
    Instant    calculadoAt
) {}
