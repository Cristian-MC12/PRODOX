// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.util;

import java.nio.charset.StandardCharsets;

/**
 * Bloque de seguridad Validación/Config (Sub-bloque 3B, C4) — límite máximo
 * de longitud de contraseña, aplicado ÚNICAMENTE cuando se CREA una
 * contraseña nueva (registro, reset), nunca al verificar una ya existente
 * (login).
 *
 * El límite es 72 BYTES UTF-8, no 72 caracteres: BCryptPasswordEncoder trunca
 * silenciosamente la entrada a los primeros 72 bytes (confirmado empíricamente
 * en la auditoría del Bloque 4 contra la versión real de spring-security-crypto
 * de este proyecto — dos contraseñas de más de 72 bytes que comparten el mismo
 * prefijo de 72 bytes producen el MISMO hash). Medir con String.length()
 * (unidades UTF-16 ≈ caracteres) sería incorrecto para cualquier contraseña
 * con caracteres multibyte (tildes, ñ, emoji): 72 caracteres de "ñ" son 144
 * bytes en UTF-8, ya truncados por BCrypt mucho antes de llegar a 72
 * caracteres — @Size(max=72) de Bean Validation no habría detectado ese caso.
 *
 * Reutilizada por AuthService.register() y ResetPasswordRequest — nunca por
 * el flujo de login, que debe seguir aceptando cualquier longitud para no
 * romper la autenticación de cuentas ya existentes.
 */
public final class PasswordPolicy {

    public static final int MAX_PASSWORD_BYTES = 72;

    private PasswordPolicy() {}

    /**
     * @throws IllegalArgumentException si password supera MAX_PASSWORD_BYTES
     *         bytes UTF-8. El mensaje de la excepción NUNCA incluye la
     *         contraseña ni su contenido.
     */
    public static void validarLongitudMaxima(String password) {
        if (password == null) {
            return; // @NotBlank en el DTO ya cubre el caso null/vacío por separado
        }
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(
                    "La contraseña no puede superar los " + MAX_PASSWORD_BYTES + " bytes (BCrypt ignora el resto silenciosamente).");
        }
    }
}
