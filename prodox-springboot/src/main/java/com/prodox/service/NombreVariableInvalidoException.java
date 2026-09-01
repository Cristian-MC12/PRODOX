// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

/**
 * Se lanza cuando una parametrización llega con un nombreVariable presente
 * pero inválido: vacío, mayor a 120 caracteres, o sin formato snake_case
 * técnico (espacios, tildes, puntuación, prosa descriptiva).
 * Extiende IllegalArgumentException para que GlobalExceptionHandler la mapee
 * a HTTP 400 si algún llamador no la captura explícitamente.
 */
public class NombreVariableInvalidoException extends IllegalArgumentException {
    public NombreVariableInvalidoException(String message) {
        super(message);
    }
}
