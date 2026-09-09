// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

/**
 * Se lanza cuando el AI Copilot no pudo completar una respuesta porque
 * Gemini falló (error HTTP del proveedor, fallo de red, timeout, o
 * respuesta que AIAgentService no pudo interpretar como texto ni como
 * function call). Antes de esta excepción (Bloque 9A, H9-4), este caso
 * terminaba como un RuntimeException genérico sin @ExceptionHandler
 * dedicado, resultando en un HTTP 500 opaco en vez de un 503 que refleje
 * que la dependencia externa (Gemini) no está disponible — mismo criterio
 * ya aplicado a PropuestaIANoDisponibleException/ReporteIANoDisponibleException/
 * RetrospectivaIANoDisponibleException. El mensaje de esta excepción está
 * pensado para mostrarse tal cual al usuario (amigable, sin detalle
 * técnico); el detalle técnico se registra aparte en el log del backend
 * por quien la lanza.
 */
public class CopilotIANoDisponibleException extends RuntimeException {
    public CopilotIANoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
