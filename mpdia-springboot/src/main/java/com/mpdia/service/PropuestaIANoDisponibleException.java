// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

/**
 * Se lanza cuando Gemini no pudo generar una propuesta de métrica con IA
 * (error HTTP del proveedor como 503/429, fallo de red, respuesta vacía o
 * JSON no parseable). Nunca debe traducirse en una propuesta "vacía" que el
 * Scrum Master pueda confundir con una generada correctamente — ver FASE 19.
 * El mensaje de esta excepción está pensado para mostrarse tal cual al
 * usuario (amigable, sin detalle técnico); el detalle técnico se registra
 * aparte en el log del backend por quien la lanza.
 */
public class PropuestaIANoDisponibleException extends RuntimeException {
    public PropuestaIANoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
