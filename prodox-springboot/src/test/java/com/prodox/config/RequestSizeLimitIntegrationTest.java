// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloque de seguridad Validación/Config (Sub-bloque 3B, C3) — prueba de
 * integración real: confirma que RequestSizeLimitFilter está efectivamente
 * registrado en el contexto real de Spring Boot (no solo en el test
 * unitario aislado) y que se ejecuta ANTES que Spring Security — un body
 * demasiado grande se rechaza con 413 incluso contra un endpoint protegido y
 * SIN Authorization header (si el orden fuera el incorrecto, se vería 401,
 * no 413).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestSizeLimitIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("Body >1MB contra un endpoint PROTEGIDO, sin Authorization: 413 (no 401) — confirma que el filtro corre antes que la autenticación")
    void bodyExcesivo_contraEndpointProtegidoSinToken_rechazado413AntesDeAutenticar() throws Exception {
        byte[] bodyDeMasDeUnMB = new byte[(int) RequestSizeLimitFilter.MAX_BODY_BYTES + 1024];

        mockMvc.perform(post("/api/proyectos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyDeMasDeUnMB))
                // Sin este filtro (o con el orden incorrecto), un POST sin
                // Authorization a un endpoint protegido devuelve 401 antes de
                // llegar al body — acá debe ser 413, la prueba de que
                // RequestSizeLimitFilter corre PRIMERO.
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    @DisplayName("Body legítimo (pequeño) contra un endpoint público sigue funcionando end-to-end con el filtro activo")
    void bodyLegitimo_siguePasandoElFiltroSinCambios() throws Exception {
        String cuerpoPequeno = "{\"email\":\"no-existe-" + System.nanoTime() + "@test.com\",\"password\":\"cualquierPassword1\"}";

        // Credenciales inválidas (400 de negocio) — lo importante es que NO
        // sea 413: el filtro deja pasar un body de pocas decenas de bytes
        // sin ningún efecto.
        String respuesta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPequeno))
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).doesNotContain("supera el tamaño máximo");
    }
}
