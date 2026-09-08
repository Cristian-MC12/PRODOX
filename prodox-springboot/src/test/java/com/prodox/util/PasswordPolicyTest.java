// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bloque de seguridad Validación/Config (Sub-bloque 3B, C4) — test unitario
 * aislado de la lógica de bytes, sin pasar por HTTP ni por Spring.
 */
class PasswordPolicyTest {

    @Test
    @DisplayName("72 bytes ASCII exactos: permitido")
    void setentaYDosBytesAscii_permitido() {
        String password = "a".repeat(72);
        assertThatCode(() -> PasswordPolicy.validarLongitudMaxima(password)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("73 bytes ASCII: rechazado, sin exponer la contraseña en el mensaje")
    void setentaYTresBytesAscii_rechazado() {
        String password = "a".repeat(73);
        assertThatThrownBy(() -> PasswordPolicy.validarLongitudMaxima(password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 bytes")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(password));
    }

    @Test
    @DisplayName("Multibyte: 40 caracteres 'ñ' = 80 bytes (>72 bytes, <=72 caracteres): rechazado")
    void multibyteMenosDe72CaracteresPeroMasDe72Bytes_rechazado() {
        String password = "ñ".repeat(40);
        assertThat(password.length()).isEqualTo(40);
        assertThatThrownBy(() -> PasswordPolicy.validarLongitudMaxima(password))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Multibyte: 36 caracteres 'ñ' = 72 bytes exactos: permitido")
    void multibyteExactamente72Bytes_permitido() {
        String password = "ñ".repeat(36);
        assertThat(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isEqualTo(72);
        assertThatCode(() -> PasswordPolicy.validarLongitudMaxima(password)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null no lanza excepción (el @NotBlank del DTO cubre ese caso por separado)")
    void nulo_noLanzaExcepcion() {
        assertThatCode(() -> PasswordPolicy.validarLongitudMaxima(null)).doesNotThrowAnyException();
    }
}
