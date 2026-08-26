// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.util.List;
import java.util.UUID;

/**
 * Evaluación detallada de una variable/métrica: todos sus registros reales (todos los sprints
 * del proyecto, ordenados por fecha ascendente), sus estadísticas globales y su desglose por sprint.
 * Fuente de datos para las gráficas de evolución, comparación entre sprints y el panel de detalle.
 */
public record MetricaEvaluacionDetalleDto(
    UUID       variableId,
    String     variableNombre,
    /** Variable.descripcion tal cual está persistida (puede ser null); el fallback a
     *  variableNombre cuando no hay descripción amigable se resuelve en presentación. */
    String     variableDescripcion,
    String     categoria,
    String     tipoAlcance,
    /** diaria | semanal | por_sprint | ilimitada (valor real de Variable.frecuenciaCaptura) */
    String     frecuenciaCaptura,
    String     formulaTexto,
    /** todos los registros de la variable, ordenados por registradoAt ascendente */
    List<RegistroPuntoDto> registros,
    VariableEstadisticasDto estadisticas,
    /** estadísticas agregadas por sprint, ordenadas por número de sprint ascendente */
    List<SprintStatsDto> porSprint
) {}
