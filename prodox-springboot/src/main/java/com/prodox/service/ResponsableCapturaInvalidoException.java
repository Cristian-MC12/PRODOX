// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

/**
 * Se lanza cuando una parametrización llega con un responsableCaptura fuera
 * del catálogo soportado (EQUIPO | SCRUM_MASTER).
 * Extiende IllegalArgumentException para que GlobalExceptionHandler la mapee
 * a HTTP 400 si algún llamador no la captura explícitamente.
 */
public class ResponsableCapturaInvalidoException extends IllegalArgumentException {
    public ResponsableCapturaInvalidoException(String message) {
        super(message);
    }
}
