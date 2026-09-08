// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Canje del código opaco de un solo uso recibido en el redirect de OAuth2
 * (?code=...) por el JWT real de sesión — ver OAuth2ExchangeCodeService.
 * El código en sí NUNCA es una credencial reutilizable: solo sirve para
 * este único intercambio, dentro de su ventana corta de vigencia.
 */
public record OAuth2ExchangeRequest(
    @NotBlank String code
) {}
