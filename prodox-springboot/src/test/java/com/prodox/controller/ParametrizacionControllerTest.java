// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// Fase 16.9.2-A: Tests para ParametrizacionController
// Fase 16.9.4: Tests para integración completa del agente GenAI
package com.prodox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.dto.AprobarParametrizacionRequest;
import com.prodox.dto.GuardarPropuestaRequest;
import com.prodox.dto.ParametrizacionRequest;
import com.prodox.dto.PropuestaParametrizacionDto;
import com.prodox.entity.MetricParametrizacion;
import com.prodox.repository.MetricParametrizacionRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.service.ParametrizacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ParametrizacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MetricParametrizacionRepository parametrizacionRepository;

    @MockBean
    private ParametrizacionService parametrizacionService;

    @MockBean
    private ProjectMemberRepository projectMemberRepository;

    @Test
    @WithMockUser(roles = "USER")
    void obtenerUltimaAprobada_conParametrizacion_retorna200() throws Exception {
        // Given
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "user")).thenReturn(true);

        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(UUID.randomUUID());
        parametrizacion.setMetricaId(metricaId);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setStatus("aprobada");
        parametrizacion.setVersion(1);
        parametrizacion.setObjetivo("Objetivo test");
        parametrizacion.setProcedimiento("Procedimiento test");
        parametrizacion.setIndicadorVariable("Variable test");
        parametrizacion.setEscala("Escala test");
        parametrizacion.setFuenteAcademica("Fuente académica");
        parametrizacion.setFormulaAcademica("Σ x");
        parametrizacion.setTipoOperacion("SUMA");
        parametrizacion.setUnidadResultado("unidad");
        parametrizacion.setUserId("user-1");
        parametrizacion.setUserEmail("user@test.com");
        
        when(parametrizacionRepository.findUltimaVersionAprobada(any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.of(parametrizacion));
        
        // When & Then
        mockMvc.perform(get("/api/parametrizacion/ultima-aprobada")
                        .param("metricaId", metricaId.toString())
                        .param("proyectoId", proyectoId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("aprobada"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.fuenteAcademica").value("Fuente académica"))
                .andExpect(jsonPath("$.formulaAcademica").value("Σ x"))
                .andExpect(jsonPath("$.tipoOperacion").value("SUMA"))
                .andExpect(jsonPath("$.unidadResultado").value("unidad"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void obtenerUltimaAprobada_sinParametrizacion_retorna204() throws Exception {
        // Given
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "user")).thenReturn(true);

        when(parametrizacionRepository.findUltimaVersionAprobada(any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/parametrizacion/ultima-aprobada")
                        .param("metricaId", metricaId.toString())
                        .param("proyectoId", proyectoId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER", username = "externo")
    @org.junit.jupiter.api.DisplayName("obtenerUltimaAprobada: usuario sin membresía en el proyecto retorna 403")
    void obtenerUltimaAprobada_usuarioExterno_retorna403() throws Exception {
        // Given
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "externo")).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/api/parametrizacion/ultima-aprobada")
                        .param("metricaId", metricaId.toString())
                        .param("proyectoId", proyectoId.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(parametrizacionRepository);
    }

    @Test
    void obtenerUltimaAprobada_sinAutenticacion_retorna401() throws Exception {
        // Given
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(get("/api/parametrizacion/ultima-aprobada")
                        .param("metricaId", metricaId.toString())
                        .param("proyectoId", proyectoId.toString()))
                .andExpect(status().isUnauthorized());
    }
    
    // ========================================
    // FASE 16.9.4: Tests del agente GenAI
    // ========================================
    
    @Test
    @WithMockUser(roles = "USER")
    void generarPropuestas_exitoso_retorna200() throws Exception {
        // Given
        ParametrizacionRequest request = new ParametrizacionRequest(
                "Factor Test",
                "Significado",
                "Métrica Test",
                "Descripción de la métrica"
        );
        
        PropuestaParametrizacionDto propuesta = new PropuestaParametrizacionDto(
                "Parametrización Test",
                "Objetivo de la métrica",
                "Procedimiento de medición",
                "Indicador principal",
                "Escala 0-100",
                "por_sprint",
                "Fuente académica",
                "Σ x",
                "SUMA",
                "unidad",
                "Justificación de la propuesta",
                "indicador_principal"
        , null, null, null, null, null, null);
        
        when(parametrizacionService.generarPropuestas(any(ParametrizacionRequest.class)))
                .thenReturn(List.of(propuesta));
        
        // When & Then
        mockMvc.perform(post("/api/parametrizacion/propuestas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("Parametrización Test"))
                .andExpect(jsonPath("$[0].objetivo").value("Objetivo de la métrica"));
    }
    
    @Test
    void generarPropuestas_sinAutenticacion_retorna401() throws Exception {
        // Given
        ParametrizacionRequest request = new ParametrizacionRequest(
                "Factor Test",
                "Significado",
                "Métrica Test",
                "Descripción"
        );

        // When & Then
        mockMvc.perform(post("/api/parametrizacion/propuestas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser(roles = "USER")
    void guardarPropuesta_exitoso_retorna201() throws Exception {
        // Given
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        
        GuardarPropuestaRequest request = new GuardarPropuestaRequest(
                metricaId,
                proyectoId,
                "Objetivo test",
                "Procedimiento test",
                "Indicador test",
                "Escala test",
                "por_sprint",
                "Fuente",
                "Formula",
                "SUMA",
                "unidad",
                "{}",
                "indicador_test"
        , null, null, null, null, null, null);

        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(UUID.randomUUID());
        parametrizacion.setMetricaId(metricaId);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);
        parametrizacion.setObjetivo("Objetivo test");
        parametrizacion.setCreatedAt(Instant.now());
        
        when(parametrizacionService.guardarPropuesta(any(GuardarPropuestaRequest.class)))
                .thenReturn(parametrizacion);
        
        // When & Then
        mockMvc.perform(post("/api/parametrizacion/guardar-propuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("propuesta"))
                .andExpect(jsonPath("$.version").value(1));
    }
    
    @Test
    void guardarPropuesta_sinAutenticacion_retorna401() throws Exception {
        // Given
        GuardarPropuestaRequest request = new GuardarPropuestaRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Objetivo",
                "Procedimiento",
                "Indicador",
                "Escala",
                "por_sprint",
                "Fuente",
                "Formula",
                "SUMA",
                "unidad",
                "{}",
                "indicador_test"
        , null, null, null, null, null, null);

        // When & Then
        mockMvc.perform(post("/api/parametrizacion/guardar-propuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser(roles = "USER")
    void aprobarParametrizacion_exitoso_retorna200() throws Exception {
        // Given
        UUID parametrizacionId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();

        AprobarParametrizacionRequest request = new AprobarParametrizacionRequest(
                "Objetivo aprobado",
                "Procedimiento aprobado",
                "Indicador aprobado",
                "Escala aprobada",
                "por_sprint",
                "Fuente académica",
                "Σ x",
                "SUMA",
                "unidades",
                "indicador_aprobado"
        , null, null, null, null, null, null);

        MetricParametrizacion existente = new MetricParametrizacion();
        existente.setId(parametrizacionId);
        existente.setProyectoId(proyectoId);
        existente.setStatus("propuesta");
        when(parametrizacionRepository.findById(parametrizacionId)).thenReturn(Optional.of(existente));

        // Revisión de aprobación: el controller ahora exige Scrum Master del
        // proyecto antes de llamar al service — @WithMockUser(roles="USER")
        // usa "user" como username por defecto.
        com.prodox.entity.ProjectMember scrumMaster = new com.prodox.entity.ProjectMember();
        scrumMaster.setProyectoId(proyectoId);
        scrumMaster.setUserId("user");
        scrumMaster.setRol("scrum_master");
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoId, "user"))
                .thenReturn(Optional.of(scrumMaster));

        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(parametrizacionId);
        parametrizacion.setStatus("aprobada");
        parametrizacion.setVersion(1);
        parametrizacion.setObjetivo("Objetivo aprobado");
        parametrizacion.setRevisadoAt(Instant.now());

        when(parametrizacionService.aprobarParametrizacion(any(UUID.class), any(AprobarParametrizacionRequest.class)))
                .thenReturn(parametrizacion);

        // When & Then
        mockMvc.perform(post("/api/parametrizacion/" + parametrizacionId + "/aprobar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("aprobada"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @WithMockUser(roles = "USER", username = "miembro-normal")
    @org.junit.jupiter.api.DisplayName("aprobarParametrizacion: scrum_member (no SM) del proyecto recibe 403")
    void aprobarParametrizacion_scrumMemberNoSM_retorna403() throws Exception {
        UUID parametrizacionId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();

        AprobarParametrizacionRequest request = new AprobarParametrizacionRequest(
                "Objetivo", "Procedimiento", "Indicador", "Escala", "por_sprint",
                "Fuente", "Formula", "SUMA", "unidad", "indicador_test"
        , null, null, null, null, null, null);

        MetricParametrizacion existente = new MetricParametrizacion();
        existente.setId(parametrizacionId);
        existente.setProyectoId(proyectoId);
        existente.setStatus("propuesta");
        when(parametrizacionRepository.findById(parametrizacionId)).thenReturn(Optional.of(existente));

        com.prodox.entity.ProjectMember miembroNormal = new com.prodox.entity.ProjectMember();
        miembroNormal.setProyectoId(proyectoId);
        miembroNormal.setUserId("miembro-normal");
        miembroNormal.setRol("scrum_member");
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoId, "miembro-normal"))
                .thenReturn(Optional.of(miembroNormal));

        mockMvc.perform(post("/api/parametrizacion/" + parametrizacionId + "/aprobar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(parametrizacionService);
    }
    
    @Test
    @WithMockUser(roles = "USER")
    void aprobarParametrizacion_noEncontrada_retorna404() throws Exception {
        // Given
        UUID parametrizacionId = UUID.randomUUID();
        
        AprobarParametrizacionRequest request = new AprobarParametrizacionRequest(
                "Objetivo",
                "Procedimiento",
                "Indicador",
                "Escala",
                "por_sprint",
                "Fuente",
                "Formula",
                "SUMA",
                "unidad",
                "indicador_test"
        , null, null, null, null, null, null);

        when(parametrizacionService.aprobarParametrizacion(any(UUID.class), any(AprobarParametrizacionRequest.class)))
                .thenThrow(new IllegalArgumentException("Parametrización no encontrada"));
        
        // When & Then
        mockMvc.perform(post("/api/parametrizacion/" + parametrizacionId + "/aprobar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
    
    @Test
    @WithMockUser(roles = "USER")
    void aprobarParametrizacion_estadoInvalido_retorna403() throws Exception {
        // Given
        UUID parametrizacionId = UUID.randomUUID();
        
        AprobarParametrizacionRequest request = new AprobarParametrizacionRequest(
                "Objetivo",
                "Procedimiento",
                "Indicador",
                "Escala",
                "por_sprint",
                "Fuente",
                "Formula",
                "SUMA",
                "unidad",
                "indicador_test"
        , null, null, null, null, null, null);

        when(parametrizacionService.aprobarParametrizacion(any(UUID.class), any(AprobarParametrizacionRequest.class)))
                .thenThrow(new IllegalStateException("Solo se pueden aprobar parametrizaciones en estado 'propuesta'"));
        
        // When & Then
        mockMvc.perform(post("/api/parametrizacion/" + parametrizacionId + "/aprobar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
    
    @Test
    void aprobarParametrizacion_sinAutenticacion_retorna401() throws Exception {
        // Given
        UUID parametrizacionId = UUID.randomUUID();

        AprobarParametrizacionRequest request = new AprobarParametrizacionRequest(
                "Objetivo",
                "Procedimiento",
                "Indicador",
                "Escala",
                "por_sprint",
                "Fuente",
                "Formula",
                "SUMA",
                "unidad",
                "indicador_test"
        , null, null, null, null, null, null);

        // When & Then
        mockMvc.perform(post("/api/parametrizacion/" + parametrizacionId + "/aprobar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
