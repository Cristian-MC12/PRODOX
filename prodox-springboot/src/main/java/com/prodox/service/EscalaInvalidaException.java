// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

/**
 * Se lanza cuando una parametrización llega con escala estructurada
 * (al menos un campo escalaTipo/escalaMin/escalaMax/escalaPaso/escalaSinLimite
 * informado) pero estructuralmente inválida: tipo no soportado, escalaMin
 * ausente, escalaPaso <= 0, escalaMax menor que escalaMin, o un valor decimal
 * en una escala declarada NUMERICA_ENTERA.
 * Extiende IllegalArgumentException para que GlobalExceptionHandler la mapee
 * a HTTP 400 si algún llamador no la captura explícitamente.
 */
public class EscalaInvalidaException extends IllegalArgumentException {
    public EscalaInvalidaException(String message) {
        super(message);
    }
}
