// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.math.BigDecimal;

/**
 * Estadísticas descriptivas de todos los registros de una variable (todos los sprints del proyecto).
 * Ver EvaluacionService para el detalle de cómo se calcula cada campo (tendencia por regresión
 * lineal, variabilidad por coeficiente de variación).
 */
public record VariableEstadisticasDto(
    Integer    totalRegistros,
    BigDecimal promedio,
    BigDecimal minimo,
    BigDecimal maximo,
    BigDecimal primerValor,
    BigDecimal ultimoValor,
    /** ultimoValor - primerValor */
    BigDecimal cambio,
    /** (cambio / primerValor) * 100; null si primerValor = 0 */
    BigDecimal cambioPct,
    /** ascendente | descendente | estable | null (si hay menos de 2 registros) */
    String     tendencia,
    /** pendiente de la regresión lineal (valor por registro), para transparencia del cálculo */
    BigDecimal pendiente,
    /** desviación estándar muestral; null si hay menos de 2 registros */
    BigDecimal desviacionEstandar,
    /** coeficiente de variación en %, null si el promedio es 0 o hay menos de 3 registros */
    BigDecimal coeficienteVariacion,
    /** baja | media | alta | null (si hay menos de 3 registros) */
    String     variabilidad
) {}
