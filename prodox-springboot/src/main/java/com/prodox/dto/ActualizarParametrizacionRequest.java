// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record ActualizarParametrizacionRequest(
    @NotBlank String objetivo,
    @NotBlank String procedimiento,
    @NotBlank String indicadorVariable,
    @NotBlank String escala,
    String escalaTipo,
    BigDecimal escalaMin,
    BigDecimal escalaMax,
    BigDecimal escalaPaso,
    Boolean escalaSinLimite,
    String escalaDescripcion,

    /**
     * Identificador técnico snake_case de la variable principal — OPCIONAL, y el
     * usuario nunca debe preocuparse por el límite de 120 caracteres. Si se informa
     * y ya es un identificador válido, se usa tal cual. Si se omite, o el valor
     * informado no es válido (típicamente porque es una descripción larga en vez de
     * un identificador corto), MetricRankingService.resolverYGuardarNombreVariable()
     * NUNCA rechaza la edición: genera automáticamente uno corto, determinista y
     * válido (máx. 120 caracteres) a partir de ese mismo texto o de
     * indicadorVariable — ver generarNombreVariableSeguro(). indicadorVariable,
     * objetivo y procedimiento nunca se truncan ni se modifican por esto.
     */
    String nombreVariable
) {}
