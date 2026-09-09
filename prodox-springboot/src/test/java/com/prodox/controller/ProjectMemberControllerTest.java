// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.service.ProjectMemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F2 — DELETE /api/project-members/{proyectoId}/{userId}.
 *
 * No existía ningún test a nivel HTTP para ProjectMemberController — solo
 * ProjectMemberServiceTest (unitario). Estos tests confirman el código HTTP
 * real que ve el frontend para cada resultado del service, y que el
 * solicitante SIEMPRE se resuelve desde el JWT (auth.getName()), nunca de
 * un valor que Angular pudiera enviar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectMemberControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ProjectMemberService service;

    private final UUID proyectoId = UUID.randomUUID();
    private final String targetUserId = "target-user-id";

    @Test
    @WithMockUser(username = "sm-user-id")
    @DisplayName("DELETE eliminar miembro: solicitud válida retorna 204 sin body")
    void eliminarMiembro_solicitudValida_retorna204() throws Exception {
        doNothing().when(service).eliminarMiembro(proyectoId, "sm-user-id", targetUserId);

        mockMvc.perform(delete("/api/project-members/" + proyectoId + "/" + targetUserId))
                .andExpect(status().isNoContent());

        // El solicitante viene EXCLUSIVAMENTE del JWT (auth.getName()) —
        // nunca de un valor que el frontend pudiera enviar en el body/query.
        verify(service).eliminarMiembro(proyectoId, "sm-user-id", targetUserId);
    }

    @Test
    @WithMockUser(username = "no-autorizado")
    @DisplayName("DELETE eliminar miembro: solicitante no autorizado (no es SM) recibe 403, sin exponer detalle interno")
    void eliminarMiembro_noAutorizado_retorna403() throws Exception {
        doThrow(new SecurityException("Solo el Scrum Master del proyecto puede eliminar miembros."))
                .when(service).eliminarMiembro(eq(proyectoId), eq("no-autorizado"), eq(targetUserId));

        String body = mockMvc.perform(delete("/api/project-members/" + proyectoId + "/" + targetUserId))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContainIgnoringCase("Exception")
                .doesNotContainIgnoringCase("stacktrace");
    }

    @Test
    @WithMockUser(username = "sm-user-id")
    @DisplayName("DELETE eliminar miembro: miembro inexistente / de otro proyecto retorna 400")
    void eliminarMiembro_miembroInexistente_retorna400() throws Exception {
        doThrow(new IllegalArgumentException("El usuario no es miembro de este proyecto."))
                .when(service).eliminarMiembro(eq(proyectoId), eq("sm-user-id"), eq(targetUserId));

        mockMvc.perform(delete("/api/project-members/" + proyectoId + "/" + targetUserId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "sm-user-id")
    @DisplayName("DELETE eliminar miembro: intentar eliminar al Scrum Master retorna 400")
    void eliminarMiembro_targetEsScrumMaster_retorna400() throws Exception {
        doThrow(new IllegalArgumentException("No se puede eliminar al Scrum Master del proyecto."))
                .when(service).eliminarMiembro(eq(proyectoId), eq("sm-user-id"), eq("sm-user-id"));

        mockMvc.perform(delete("/api/project-members/" + proyectoId + "/sm-user-id"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE eliminar miembro: sin autenticar retorna 401")
    void eliminarMiembro_sinAutenticar_retorna401() throws Exception {
        mockMvc.perform(delete("/api/project-members/" + proyectoId + "/" + targetUserId))
                .andExpect(status().isUnauthorized());
    }

    // ── Regresión: el resto del controller sigue funcionando sin cambios ───

    @Test
    @WithMockUser(username = "sm-user-id")
    @DisplayName("Regresión: GET listar miembros sigue funcionando sin cambios tras agregar DELETE")
    void listarMiembros_siguefuncionando() throws Exception {
        org.mockito.Mockito.when(service.listarMiembros(proyectoId, "sm-user-id"))
                .thenReturn(java.util.List.of());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/project-members/" + proyectoId))
                .andExpect(status().isOk());
    }
}
