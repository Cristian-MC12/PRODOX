// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

/**
 * Se lanza cuando Gemini no pudo generar un reporte ejecutivo de sprint
 * (error HTTP del proveedor —incluida cuota agotada 429 RESOURCE_EXHAUSTED—,
 * fallo de red, respuesta vacía, o respuesta que no contiene ninguna de las
 * secciones esperadas). Nunca debe traducirse en un reporte "vacío" o con
 * texto de relleno que el Scrum Master pueda confundir con uno generado
 * correctamente — mismo criterio que PropuestaIANoDisponibleException (FASE 19),
 * aplicado aquí a AIReportService tras el HTTP 500 opaco detectado en FASE 22.
 * El mensaje de esta excepción está pensado para mostrarse tal cual al
 * usuario (amigable, sin detalle técnico); el detalle técnico se registra
 * aparte en el log del backend por quien la lanza.
 */
public class ReporteIANoDisponibleException extends RuntimeException {
    public ReporteIANoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
