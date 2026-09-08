// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import com.prodox.util.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8) String newPassword
) {
    // Bloque de seguridad Validación/Config (C4): newPassword es SIEMPRE una
    // contraseña nueva (nunca se usa para verificar una existente, a
    // diferencia de AuthRequest.password), así que este DTO puede aplicar el
    // límite de bytes de BCrypt directamente, sin ningún riesgo de afectar login.
    public ResetPasswordRequest {
        PasswordPolicy.validarLongitudMaxima(newPassword);
    }
}
