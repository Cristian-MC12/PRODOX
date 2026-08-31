// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

/**
 * Se lanza cuando una variable 'individual' tiene mas de un registro para el
 * periodo y su tipoOperacion (DIRECTO o FORMULA) necesita reducirlos a un
 * unico valor, pero Variable.agregacionMiembros no esta configurado.
 *
 * Deliberadamente NO se asume SUMA ni PROMEDIO por defecto, ni se toma
 * silenciosamente el registro mas reciente: el dato de cada miembro se
 * conserva intacto y el calculo se rechaza de forma explicita hasta que se
 * configure la regla de agregacion correspondiente.
 *
 * Extiende IllegalArgumentException para mapear a HTTP 400 sin tener que
 * tocar los catch existentes de CalculoMetricaController.
 */
public class AgregacionMiembrosNoConfiguradaException extends IllegalArgumentException {
    public AgregacionMiembrosNoConfiguradaException(String message) {
        super(message);
    }
}
