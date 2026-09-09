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
import com.prodox.ratelimit.RateLimitService;
import com.prodox.repository.MetricParametrizacionRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.service.ParametrizacionService;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Autowired
    private RateLimitService rateLimitService;

    /**
     * Bloque 10B-2: RateLimitService es un bean real (no mockeado) —
     * mismo patrón que AICopilotControllerTest. Se limpia antes de cada
     * test para que el contador de un test no contamine el siguiente
     * (el contexto Spring, y por tanto el singleton, se comparte entre
     * todos los tests de esta clase).
     */
    @BeforeEach
    void resetRateLimit() {
        rateLimitService.resetAll();
    }

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

    // ========================================
    // Bloque 12B (P0-4): SQL injection — parámetro tipado como UUID
    // ========================================

    @Test
    @WithMockUser(roles = "USER")
    @org.junit.jupiter.api.DisplayName("SQLi: un payload SQL en un parámetro UUID nunca llega a la query — rechazado 400 por conversión de tipo")
    void obtenerUltimaAprobada_metricaIdConPayloadSQLi_rechazado400SinLlegarALaQuery() throws Exception {
        String payloadSQLi = "'; DROP TABLE metric_parametrizaciones; --";

        String body = mockMvc.perform(get("/api/parametrizacion/ultima-aprobada")
                        .param("metricaId", payloadSQLi)
                        .param("proyectoId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        // Spring MVC rechaza la conversión String -> UUID antes de que el
        // controller/service/repositorio vean el valor: la query nunca se
        // construye con el payload. Se verifica además que el 400 no
        // filtra información interna.
        assertThat(body).doesNotContainIgnoringCase("Exception")
                .doesNotContainIgnoringCase("stacktrace")
                .doesNotContainIgnoringCase("SELECT ");

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

    // ========================================
    // Bloque 12B (P0-2): BOLA cross-tenant en aprobación
    // ========================================

    @Test
    @WithMockUser(roles = "USER", username = "sm-proyecto-a")
    @org.junit.jupiter.api.DisplayName("BOLA cross-tenant: Scrum Master del proyecto A no puede aprobar una parametrización del proyecto B, aunque sea SM en A")
    void aprobarParametrizacion_scrumMasterDeOtroProyecto_denegadoSinFiltrarDatosAjenos() throws Exception {
        UUID proyectoA = UUID.randomUUID();
        UUID proyectoB = UUID.randomUUID();
        UUID parametrizacionId = UUID.randomUUID();

        AprobarParametrizacionRequest request = new AprobarParametrizacionRequest(
                "Objetivo", "Procedimiento", "Indicador", "Escala", "por_sprint",
                "Fuente", "Formula", "SUMA", "unidad", "indicador_test"
        , null, null, null, null, null, null);

        MetricParametrizacion parametrizacionDeB = new MetricParametrizacion();
        parametrizacionDeB.setId(parametrizacionId);
        parametrizacionDeB.setProyectoId(proyectoB);
        parametrizacionDeB.setStatus("propuesta");
        parametrizacionDeB.setObjetivo("DATO CONFIDENCIAL DEL PROYECTO B");
        when(parametrizacionRepository.findById(parametrizacionId)).thenReturn(Optional.of(parametrizacionDeB));

        // sm-proyecto-a ES scrum_master real, pero del proyecto A — sin membresía en B.
        com.prodox.entity.ProjectMember smDeA = new com.prodox.entity.ProjectMember();
        smDeA.setProyectoId(proyectoA);
        smDeA.setUserId("sm-proyecto-a");
        smDeA.setRol("scrum_master");
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoA, "sm-proyecto-a"))
                .thenReturn(Optional.of(smDeA));
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoB, "sm-proyecto-a"))
                .thenReturn(Optional.empty());

        String body = mockMvc.perform(post("/api/parametrizacion/" + parametrizacionId + "/aprobar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        // La respuesta 403 no filtra el contenido del recurso ajeno.
        assertThat(body).doesNotContain("DATO CONFIDENCIAL DEL PROYECTO B");

        // El rechazo ocurre en el controller (chequeo contra existente.getProyectoId(),
        // nunca contra un proyectoId provisto por el cliente) — el service de negocio
        // nunca llega a invocarse.
        verifyNoInteractions(parametrizacionService);
    }

    @Test
    @WithMockUser(roles = "USER", username = "sm-proyecto-b")
    @org.junit.jupiter.api.DisplayName("BOLA cross-tenant: el Scrum Master REAL del proyecto B sí puede aprobar su propia parametrización")
    void aprobarParametrizacion_scrumMasterDelProyectoCorrecto_permitido() throws Exception {
        UUID proyectoB = UUID.randomUUID();
        UUID parametrizacionId = UUID.randomUUID();

        AprobarParametrizacionRequest request = new AprobarParametrizacionRequest(
                "Objetivo", "Procedimiento", "Indicador", "Escala", "por_sprint",
                "Fuente", "Formula", "SUMA", "unidad", "indicador_test"
        , null, null, null, null, null, null);

        MetricParametrizacion parametrizacionDeB = new MetricParametrizacion();
        parametrizacionDeB.setId(parametrizacionId);
        parametrizacionDeB.setProyectoId(proyectoB);
        parametrizacionDeB.setStatus("propuesta");
        when(parametrizacionRepository.findById(parametrizacionId)).thenReturn(Optional.of(parametrizacionDeB));

        com.prodox.entity.ProjectMember smDeB = new com.prodox.entity.ProjectMember();
        smDeB.setProyectoId(proyectoB);
        smDeB.setUserId("sm-proyecto-b");
        smDeB.setRol("scrum_master");
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoB, "sm-proyecto-b"))
                .thenReturn(Optional.of(smDeB));

        MetricParametrizacion aprobada = new MetricParametrizacion();
        aprobada.setId(parametrizacionId);
        aprobada.setStatus("aprobada");
        aprobada.setVersion(1);
        aprobada.setObjetivo("Objetivo");
        when(parametrizacionService.aprobarParametrizacion(eq(parametrizacionId), any(AprobarParametrizacionRequest.class)))
                .thenReturn(aprobada);

        mockMvc.perform(post("/api/parametrizacion/" + parametrizacionId + "/aprobar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("aprobada"));
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

    // ========================================
    // Bloque 10B-2: Rate limiting en /propuestas (llama a Gemini)
    // ========================================

    private ParametrizacionRequest requestPropuestaValido() {
        return new ParametrizacionRequest(
                "Factor Test",
                "Significado",
                "Métrica Test",
                "Descripción de la métrica"
        );
    }

    private PropuestaParametrizacionDto propuestaMock() {
        return new PropuestaParametrizacionDto(
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
    }

    @Test
    @WithMockUser(roles = "USER", username = "user-rl")
    void generarPropuestas_dentroDelLimite_retorna200() throws Exception {
        when(parametrizacionService.generarPropuestas(any(ParametrizacionRequest.class)))
                .thenReturn(List.of(propuestaMock()));

        // Límite default en test = 10 (prodox.ai.rate-limit.requests-per-minute)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/parametrizacion/propuestas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestPropuestaValido())))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(roles = "USER", username = "user-rl")
    @org.junit.jupiter.api.DisplayName("generarPropuestas: request número 11 en la ventana recibe 429 y NO llama a Gemini")
    void generarPropuestas_excedeLimite_retorna429SinLlamarAGemini() throws Exception {
        when(parametrizacionService.generarPropuestas(any(ParametrizacionRequest.class)))
                .thenReturn(List.of(propuestaMock()));

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/parametrizacion/propuestas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestPropuestaValido())))
                    .andExpect(status().isOk());
        }

        // Reiniciar el mock para poder verificar de forma limpia que la
        // request 11 (bloqueada) nunca llega al service.
        org.mockito.Mockito.clearInvocations(parametrizacionService);

        mockMvc.perform(post("/api/parametrizacion/propuestas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestPropuestaValido())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").exists())
                // El body de 429 no debe filtrar detalles internos (stack, excepción, prompts).
                .andExpect(jsonPath("$.error", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Exception"))));

        verifyNoInteractions(parametrizacionService);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("generarPropuestas: el límite de un usuario no afecta a otro usuario distinto")
    void generarPropuestas_usuariosDistintos_noComparteContador() throws Exception {
        when(parametrizacionService.generarPropuestas(any(ParametrizacionRequest.class)))
                .thenReturn(List.of(propuestaMock()));

        // Agotar el límite del usuario A
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/parametrizacion/propuestas")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("usuario-a").roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestPropuestaValido())))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/parametrizacion/propuestas")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("usuario-a").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestPropuestaValido())))
                .andExpect(status().isTooManyRequests());

        // El usuario B, con su propio contador en 0, todavía puede hacer requests.
        mockMvc.perform(post("/api/parametrizacion/propuestas")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("usuario-b").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestPropuestaValido())))
                .andExpect(status().isOk());
    }
}
