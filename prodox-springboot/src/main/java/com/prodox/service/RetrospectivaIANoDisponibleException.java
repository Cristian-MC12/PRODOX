// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

/**
 * Se lanza cuando Gemini no pudo generar una retrospectiva de sprint (error
 * HTTP del proveedor —incluida cuota agotada 429 RESOURCE_EXHAUSTED—, fallo
 * de red, respuesta vacía, o respuesta que no contiene ninguna de las
 * secciones esperadas). Nunca debe traducirse en una retrospectiva "vacía" o
 * con texto de relleno que el equipo pueda confundir con una generada
 * correctamente — mismo criterio que ReporteIANoDisponibleException (FASE 23),
 * aplicado aquí a AIRetrospectiveService tras confirmar en FASE 24 que tenía
 * el mismo defecto (HTTP 500 opaco) que Reportes antes de esa fase.
 * El mensaje de esta excepción está pensado para mostrarse tal cual al
 * usuario (amigable, sin detalle técnico); el detalle técnico (causa
 * original) se conserva como cause() para diagnóstico en el log del backend.
 */
public class RetrospectivaIANoDisponibleException extends RuntimeException {
    public RetrospectivaIANoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
