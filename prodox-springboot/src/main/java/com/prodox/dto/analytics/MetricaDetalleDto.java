// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.analytics;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Detalle de una métrica individual en un sprint.
 * 
 * Contiene el resultado calculado de la métrica con su contexto completo:
 * dirección deseable, valor objetivo (si aplica), y unidad de medida.
 * 
 * V50 — Fase 4: Calidad analítica de módulos de IA (Prioridad 1)
 * 
 * Fuente de datos: Variable (modelo antiguo) con join a Metrica.
 * Cada registro representa una variable activa con resultados en el sprint.
 */
public record MetricaDetalleDto(
    /** ID de la variable (NO metricaId - puede haber varias variables por métrica) */
    UUID variableId,
    
    /** ID de la métrica en el catálogo global */
    UUID metricaId,
    
    /** Código único de la métrica (ej: "COV_CODE") */
    String metricaCodigo,
    
    /** Nombre de la variable (puede ser específico del proyecto) */
    String variableNombre,
    
    /** Nombre de la métrica (del catálogo global) */
    String metricaNombre,
    
    /** Categoría (ej: "Calidad") */
    String categoria,
    
    /** Promedio calculado para esta variable en el sprint */
    BigDecimal promedio,
    
    /** Valor mínimo registrado */
    BigDecimal min,
    
    /** Valor máximo registrado */
    BigDecimal max,
    
    /** Total de registros que produjeron este resultado */
    Integer totalRegistros,
    
    /** 
     * Dirección deseable de la métrica: 
     * MAYOR_MEJOR | MENOR_MEJOR | OBJETIVO | null 
     */
    String direccionDeseable,
    
    /** 
     * Valor objetivo (solo si direccionDeseable='OBJETIVO', null en otros casos).
     * Precisión: 15 dígitos, 4 decimales.
     */
    BigDecimal valorObjetivo,
    
    /** Tipo de alcance: grupal | individual */
    String tipoAlcance,
    
    /** Fórmula con la que se midió (contexto para el evaluador) */
    String formulaTexto,
    
    /** Frecuencia de captura: diaria | semanal | por_sprint */
    String frecuenciaCaptura
) {
    /**
     * Calcula el porcentaje de cumplimiento del objetivo.
     * 
     * @return Porcentaje (0-100+) si direccionDeseable='OBJETIVO' y valorObjetivo != null,
     *         null en otros casos.
     */
    public BigDecimal porcentajeCumplimientoObjetivo() {
        if (!"OBJETIVO".equals(direccionDeseable) || valorObjetivo == null || valorObjetivo.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        
        // (promedio / objetivo) * 100
        return promedio.divide(valorObjetivo, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
    
    /**
     * Indica si el resultado tiene dirección deseable configurada.
     */
    public boolean tieneDireccionDeseable() {
        return direccionDeseable != null;
    }
    
    /**
     * Indica si la métrica busca un objetivo específico.
     */
    public boolean esMetricaObjetivo() {
        return "OBJETIVO".equals(direccionDeseable);
    }
}

