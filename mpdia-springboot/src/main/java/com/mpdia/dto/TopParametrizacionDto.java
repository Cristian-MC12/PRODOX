// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Una entrada del top 3 de parametrizaciones más usadas de un factor.
 *
 * Incluye los campos académicos (fuenteAcademica/formulaAcademica/tipoOperacion/
 * unidadResultado) y frecuenciaCaptura para que "Usar" (MetricRankingService,
 * frontend parametrizacion.component.ts) pueda reutilizar la parametrización
 * COMPLETA en vez de copiar solo objetivo/procedimiento/indicadorVariable/escala
 * y descartar el resto — bug corregido en la revisión de Ejecución/Parametrización.
 */
public record TopParametrizacionDto(
    UUID    id,
    String  userEmail,
    String  objetivo,
    String  procedimiento,
    String  indicadorVariable,
    String  escala,
    int     usos,
    Instant createdAt,
    String  frecuenciaCaptura,
    String  fuenteAcademica,
    String  formulaAcademica,
    String  tipoOperacion,
    String  unidadResultado
) {}
