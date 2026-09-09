// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.service.ProyectoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloque 12B (P1-5) — GlobalExceptionHandler.handleGeneric(): el catch-all
 * de cualquier Exception no controlada. Ningún test existente lo ejercitaba
 * directamente (HttpSecurityHeadersTest cubre 400/401/404, no 500).
 *
 * Se fuerza una excepción real e inesperada (NullPointerException, nunca
 * lanzada a propósito por lógica de negocio) desde un servicio mockeado,
 * contra un controller y GlobalExceptionHandler REALES — no se inspecciona
 * el código Java, se inspecciona la respuesta HTTP real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ProyectoService proyectoService;

    @Test
    @DisplayName("Excepción inesperada real (NPE) -> 500 genérico, sin stack trace, sin nombre de clase interna, sin mensaje original")
    @WithMockUser(username = "geh-security-test")
    void excepcionInesperada_retorna500GenericoSinFiltrarInformacionInterna() throws Exception {
        String mensajeInternoSensible = "NullPointerException interna: campo config.datasource.password era null";
        when(proyectoService.listarMisProyectos("geh-security-test"))
                .thenThrow(new NullPointerException(mensajeInternoSensible));

        String body = mockMvc.perform(get("/api/proyectos/mios"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContainIgnoringCase("NullPointerException")
                .doesNotContain(mensajeInternoSensible)
                .doesNotContainIgnoringCase("stacktrace")
                .doesNotContainIgnoringCase("at com.prodox")
                .doesNotContainIgnoringCase("java.lang")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("token")
                .doesNotContainIgnoringCase("credencial");

        // Mensaje genérico fijo (GlobalExceptionHandler.handleGeneric()),
        // igual para cualquier excepción no controlada — nunca el mensaje real.
        assertThat(body).contains("Error interno del servidor");
    }
}
