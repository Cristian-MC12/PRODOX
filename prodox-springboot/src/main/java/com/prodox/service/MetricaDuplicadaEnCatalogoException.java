// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.MetricaDto;

/**
 * Se lanza cuando MetricaIAService.crearDesdeConfirmacion() detecta que ya
 * existe una métrica en el catálogo global con el mismo nombre normalizado
 * (ignorando mayúsculas/espacios). En vez de crear una fila duplicada,
 * transporta la métrica existente para que el controller pueda devolverla
 * al frontend y este pueda ofrecer "reutilizar la métrica existente" en vez
 * de fallar con un error genérico.
 */
public class MetricaDuplicadaEnCatalogoException extends RuntimeException {

    private final MetricaDto metricaExistente;

    public MetricaDuplicadaEnCatalogoException(String message, MetricaDto metricaExistente) {
        super(message);
        this.metricaExistente = metricaExistente;
    }

    public MetricaDto getMetricaExistente() {
        return metricaExistente;
    }
}
