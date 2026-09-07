// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0 seguridad — DevResetController NO debe existir como bean/endpoints
 * accesibles fuera de un entorno de desarrollo explícito.
 *
 * La defensa principal es estructural (@Profile("dev") en el propio
 * controller — ver DevResetController): sin ese perfil activo, Spring no
 * registra ningún handler bajo /api/dev/**. Esto es distinto de "devolver
 * 401/403 desde dentro del método" — acá no hay método que invocar.
 *
 * Comportamiento HTTP real esperado, y por qué (Spring Security corre ANTES
 * que el enrutamiento de Spring MVC):
 *   - Request SIN autenticar a /api/dev/** -> 401. La regla de cierre
 *     .anyRequest().authenticated() de SecurityConfig intercepta la
 *     petición ANTES de que Spring MVC llegue a buscar un handler — nunca
 *     se entera de que la ruta no existe.
 *   - Request AUTENTICADA (cualquier rol) a /api/dev/** sin el perfil dev
 *     activo -> 404. Pasa el filtro de Spring Security, pero
 *     DispatcherServlet no tiene ningún HandlerMapping registrado para esa
 *     ruta porque el bean del controller nunca se creó.
 */
class DevResetControllerSecurityTest {

    /**
     * Grupo A — SIN perfil "dev" activo (equivalente a producción: Railway
     * activa únicamente SPRING_PROFILES_ACTIVE=prod, nunca "dev" — ver
     * RAILWAY_DEPLOYMENT.md). Usa el mismo perfil "test" que el resto de la
     * suite de integración — ninguna de sus propiedades tiene relación con
     * "dev".
     */
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @DisplayName("Sin perfil dev activo (equivalente a producción)")
    class SinPerfilDev {

        @Autowired private MockMvc mockMvc;
        @Autowired private ApplicationContext applicationContext;
        @Autowired private ObjectMapper objectMapper;

        @Test
        @DisplayName("DevResetController no se registra como bean sin el perfil dev")
        void devResetControllerBean_noSeRegistra() {
            assertThat(applicationContext.getBeanNamesForType(DevResetController.class)).isEmpty();
        }

        @Test
        @DisplayName("A) DELETE /api/dev/reset-proyectos sin autenticar -> 401 (bloqueado por Security antes de rutear)")
        void resetProyectos_sinAutenticar_retorna401() throws Exception {
            mockMvc.perform(delete("/api/dev/reset-proyectos"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        @DisplayName("A) DELETE /api/dev/reset-proyectos autenticado (usuario normal) -> 404, la ruta no existe")
        void resetProyectos_autenticado_retorna404() throws Exception {
            mockMvc.perform(delete("/api/dev/reset-proyectos"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("B) GET /api/dev/diagnostico sin autenticar -> 401")
        void diagnostico_sinAutenticar_retorna401() throws Exception {
            mockMvc.perform(get("/api/dev/diagnostico"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        @DisplayName("B) GET /api/dev/diagnostico autenticado (usuario normal) -> 404, la ruta no existe")
        void diagnostico_autenticado_retorna404() throws Exception {
            mockMvc.perform(get("/api/dev/diagnostico"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser
        @DisplayName("E) usuario autenticado normal no puede ejecutar reset en configuración tipo producción")
        void usuarioNormalAutenticado_noPuedeEjecutarReset() throws Exception {
            mockMvc.perform(delete("/api/dev/reset-proyectos"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "SCRUM_MASTER")
        @DisplayName("F) SCRUM_MASTER autenticado tampoco puede ejecutar reset — el rol/pertenencia de proyecto no habilita herramientas dev")
        void scrumMasterAutenticado_tampocoPuedeEjecutarReset() throws Exception {
            mockMvc.perform(delete("/api/dev/reset-proyectos"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/dev/diagnostico"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("G) /api/health sigue público sin regresión")
        void health_siguePublico() throws Exception {
            mockMvc.perform(get("/api/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("G) POST /api/auth/login sigue público sin regresión (llega al controller: 400 por validación, nunca 401 de Security)")
        void login_siguePublico() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("G) POST /api/auth/register sigue público sin regresión (llega al controller: 400 por validación, nunca 401 de Security)")
        void register_siguePublico() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of())))
                    .andExpect(status().isBadRequest());
        }
    }

    /**
     * Grupo C — CON perfil "dev" explícitamente activo. Confirma que el
     * controller SÍ se registra cuando alguien lo activa a propósito, y que
     * un GET sobre la ruta destructiva jamás dispara el borrado (solo hay
     * un @DeleteMapping para /reset-proyectos; un GET no calza con ningún
     * handler). No se invoca el DELETE real en ningún test — ejecutarlo
     * borraría datos de la base de datos local compartida por el resto de
     * la suite de integración; la prueba de "el bean existe y la ruta
     * responde" se hace contra /diagnostico, que es de solo lectura.
     */
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("dev")
    @DisplayName("Con perfil dev explícitamente activo")
    class ConPerfilDev {

        @Autowired private MockMvc mockMvc;
        @Autowired private ApplicationContext applicationContext;

        @Test
        @DisplayName("C) DevResetController SÍ se registra como bean cuando el perfil dev está activo")
        void devResetControllerBean_siSeRegistra() {
            assertThat(applicationContext.getBeanNamesForType(DevResetController.class)).hasSize(1);
        }

        @Test
        @WithMockUser
        @DisplayName("C) GET /api/dev/diagnostico con perfil dev y autenticado -> 200, la ruta existe y responde")
        void diagnostico_conPerfilDevYAutenticado_respondeOk() throws Exception {
            mockMvc.perform(get("/api/dev/diagnostico"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("C) GET /api/dev/diagnostico con perfil dev pero SIN autenticar -> 401 (el permitAll global fue retirado de SecurityConfig)")
        void diagnostico_conPerfilDevSinAutenticar_siguePidiendoAutenticacion() throws Exception {
            mockMvc.perform(get("/api/dev/diagnostico"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        @DisplayName("D) GET /api/dev/reset-proyectos (verbo incorrecto) -> 405, nunca ejecuta el borrado — solo existe @DeleteMapping")
        void resetProyectos_metodoGet_noEjecutaBorrado() throws Exception {
            mockMvc.perform(get("/api/dev/reset-proyectos"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
