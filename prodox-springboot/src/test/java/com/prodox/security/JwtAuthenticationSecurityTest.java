// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloque 12B (P0-1) — JWT contra el filtro de autenticación REAL.
 *
 * A diferencia de JwtUtilTest (que prueba JwtUtil de forma aislada, sin
 * pasar por HTTP), estos tests atraviesan el stack completo: MockMvc real
 * → JwtAuthFilter real → SecurityConfig real → AuthenticationEntryPoint
 * real. Usan /api/proyectos/mios como endpoint protegido representativo
 * (requiere Authentication, no requiere datos previos en BD para el caso
 * exitoso — devuelve lista vacía si el usuario no tiene proyectos).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthenticationSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    /** Mismo secret que usa la app en este perfil — nunca hardcodeado en el test. */
    @Value("${prodox.jwt.secret}")
    private String jwtSecret;

    @Test
    @DisplayName("JWT válido: permite acceso al endpoint protegido")
    void jwtValido_permiteAcceso() throws Exception {
        String token = jwtUtil.generateToken(
                "jwt-security-test-" + System.nanoTime(), "jwt-sec@test.com", "scrum_member");

        mockMvc.perform(get("/api/proyectos/mios").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sin header Authorization: rechazado 401")
    void sinAuthorizationHeader_rechazado401() throws Exception {
        mockMvc.perform(get("/api/proyectos/mios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT inválido (cadena arbitraria, no un JWT): rechazado 401 sin filtrar información")
    void jwtInvalido_cadenaArbitraria_rechazado401SinFiltrarInfo() throws Exception {
        String basura = "esto-no-es-un-jwt-en-absoluto";

        String body = mockMvc.perform(get("/api/proyectos/mios")
                        .header("Authorization", "Bearer " + basura))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContainIgnoringCase("Exception")
                .doesNotContainIgnoringCase("stacktrace")
                .doesNotContainIgnoringCase("at com.prodox")
                .doesNotContain(basura);
    }

    @Test
    @DisplayName("JWT expirado (firmado con el secret real, exp en el pasado): rechazado 401 sin filtrar información")
    void jwtExpirado_rechazado401SinFiltrarInfo() throws Exception {
        String tokenExpirado = Jwts.builder()
                .setSubject("jwt-expirado-test")
                .claim("email", "expirado@test.com")
                .claim("role", "scrum_member")
                .setIssuedAt(new Date(System.currentTimeMillis() - 20_000))
                .setExpiration(new Date(System.currentTimeMillis() - 10_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
                .compact();

        String body = mockMvc.perform(get("/api/proyectos/mios")
                        .header("Authorization", "Bearer " + tokenExpirado))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContainIgnoringCase("Exception")
                .doesNotContainIgnoringCase("stacktrace")
                .doesNotContainIgnoringCase("expired")
                .doesNotContain(tokenExpirado);
    }

    @Test
    @DisplayName("JWT manipulado (firma alterada tras generarlo válido): rechazado 401 sin filtrar información")
    void jwtManipulado_firmaAlterada_rechazado401SinFiltrarInfo() throws Exception {
        String tokenValido = jwtUtil.generateToken(
                "jwt-tamper-test-" + System.nanoTime(), "tamper@test.com", "scrum_member");
        char ultimo = tokenValido.charAt(tokenValido.length() - 1);
        String tokenManipulado = tokenValido.substring(0, tokenValido.length() - 1)
                + (ultimo == 'a' ? 'b' : 'a');

        String body = mockMvc.perform(get("/api/proyectos/mios")
                        .header("Authorization", "Bearer " + tokenManipulado))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContainIgnoringCase("Exception")
                .doesNotContainIgnoringCase("stacktrace")
                .doesNotContainIgnoringCase("signature");
    }

    @Test
    @DisplayName("JWT sin prefijo 'Bearer ': tratado como no autenticado, rechazado 401")
    void jwtSinPrefijoBearer_rechazado401() throws Exception {
        String token = jwtUtil.generateToken("jwt-sin-bearer", "sinbearer@test.com", "scrum_member");

        mockMvc.perform(get("/api/proyectos/mios").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }
}
