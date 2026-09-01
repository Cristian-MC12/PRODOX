// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MetricParametrizacionDto(
    UUID    id,
    Integer version,
    UUID    factorId,
    String  factorNombre,
    String  factorCategoria,
    String  userEmail,
    String  objetivo,
    String  procedimiento,
    String  indicadorVariable,
    String  escala,
    String  frecuenciaCaptura,
    UUID    metricaBaseId,
    String  status,
    String  revisadoPor,
    Instant revisadoAt,
    String  motivoRechazo,
    UUID    proyectoId,
    Instant createdAt,
    String  propuestaIAJson,
    String  configuracionAprobadaJson,
    String  fuenteAcademica,
    String  formulaAcademica,
    String  tipoOperacion,
    String  unidadResultado,
    /** Revisión de captura por parametrización: "EQUIPO" | "SCRUM_MASTER". */
    String  responsableCaptura,
    /** Escala estructurada — ver ParametrizacionService.validarEscalaEstructurada(). */
    String     escalaTipo,
    BigDecimal escalaMin,
    BigDecimal escalaMax,
    BigDecimal escalaPaso,
    Boolean    escalaSinLimite,
    String     escalaDescripcion
) {}
