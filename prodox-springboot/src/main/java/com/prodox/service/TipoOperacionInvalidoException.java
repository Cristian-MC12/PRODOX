// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

/**
 * Se lanza cuando una parametrización llega con un tipoOperacion fuera del
 * catálogo oficial soportado por el motor de cálculo (SUMA | PROMEDIO | DIRECTO | FORMULA).
 * Extiende IllegalArgumentException para que GlobalExceptionHandler la mapee
 * a HTTP 400 si algún llamador no la captura explícitamente.
 */
public class TipoOperacionInvalidoException extends IllegalArgumentException {
    public TipoOperacionInvalidoException(String message) {
        super(message);
    }
}
