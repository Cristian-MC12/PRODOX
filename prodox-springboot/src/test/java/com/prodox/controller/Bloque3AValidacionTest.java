// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.dto.*;
import com.prodox.dto.ai.AIInsightDto;
import com.prodox.dto.ai.UpdateInsightDto;
import com.prodox.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bloque de seguridad 3A — endurecimiento de validación (C1/C2).
 *
 * Cubre exactamente los DTOs/endpoints identificados en la auditoría del
 * Bloque 3 sin @Valid o sin @Size/@NotNull suficiente: confirma que un
 * cuerpo válido sigue funcionando, que un campo obligatorio vacío/null se
 * rechaza cuando corresponde, que un campo que supera el máximo se rechaza
 * con 400 (nunca con el 500/409 que producía antes), que la validación
 * ocurre en el backend (MockMvc golpea el controller real, no el frontend),
 * y que el cuerpo de un 400 no expone stack traces, SQL ni el prompt/valor
 * completo enviado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Bloque3AValidacionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private CalculoMetricaService calculoMetricaService;
    @MockBean private AIInsightsService aiInsightsService;
    @MockBean private VariableService variableService;
    @MockBean private VariableDinamicaService variableDinamicaService;
    @MockBean private MetricaAcademicaService metricaAcademicaService;
    @MockBean private MetricaIAService metricaIAService;
    @MockBean private ParametrizacionService parametrizacionService;

    private static String repetir(String s, int n) {
        return s.repeat(n);
    }

    // ─────────────────────────────────────────────────────────────────
    // CalculoMetricaController / CalcularMetricaRequest — @NotNull
    // ─────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "test@test.com")
    void calcular_proyectoIdNull_retorna400NoQuinientos() throws Exception {
        UUID metricaId = UUID.randomUUID();
        String body = "{\"proyectoId\":null,\"sprintId\":\"" + UUID.randomUUID() + "\"}";

        mockMvc.perform(post("/api/metricas/" + metricaId + "/calcular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.proyectoId").exists());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void calcular_sprintIdNull_retorna400() throws Exception {
        UUID metricaId = UUID.randomUUID();
        String body = "{\"proyectoId\":\"" + UUID.randomUUID() + "\",\"sprintId\":null}";

        mockMvc.perform(post("/api/metricas/" + metricaId + "/calcular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.sprintId").exists());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void calcular_cuerpoValido_siguePasandoAlServicio() throws Exception {
        UUID metricaId = UUID.randomUUID();
        CalcularMetricaRequest req = new CalcularMetricaRequest(UUID.randomUUID(), UUID.randomUUID());
        ResultadoMetricaDto resultado = new ResultadoMetricaDto(
                UUID.randomUUID(), metricaId, "Velocidad", req.proyectoId(), req.sprintId(),
                UUID.randomUUID(), 1, "SUMA", "x", "[]", BigDecimal.TEN, "puntos", "ok", null, Instant.now());
        when(calculoMetricaService.calcularMetrica(any(), any(), anyString())).thenReturn(resultado);

        mockMvc.perform(post("/api/metricas/" + metricaId + "/calcular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────────
    // AIInsightsController / UpdateInsightDto — @Size(max=200) en title
    // ─────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "test@test.com")
    void updateInsight_tituloExcedeLimite_retorna400() throws Exception {
        UUID insightId = UUID.randomUUID();
        UpdateInsightDto dto = new UpdateInsightDto(repetir("a", 201), "descripcion válida", null);

        mockMvc.perform(put("/api/ai/insights/" + insightId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.title").exists());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void updateInsight_tituloEnElLimite_siguePasandoAlServicio() throws Exception {
        UUID insightId = UUID.randomUUID();
        UpdateInsightDto dto = new UpdateInsightDto(repetir("a", 200), "descripcion válida", "recomendación");
        AIInsightDto respuesta = new AIInsightDto(insightId, UUID.randomUUID(), null, "TREND", "MEDIUM",
                dto.title(), dto.description(), List.of(), dto.recommendation(), "MEDIUM", false, Instant.now());
        when(aiInsightsService.updateInsight(any(), any(), anyString())).thenReturn(respuesta);

        mockMvc.perform(put("/api/ai/insights/" + insightId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────────
    // VariableController / ActualizarFormulaRequest — @Size(max=20)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "test@test.com")
    void actualizarFormula_frecuenciaExcedeLimite_retorna400() throws Exception {
        UUID proyectoId = UUID.randomUUID();
        UUID variableId = UUID.randomUUID();
        ActualizarFormulaRequest req = new ActualizarFormulaRequest("ISE = A + B", null, repetir("x", 21));

        mockMvc.perform(patch("/api/proyectos/" + proyectoId + "/variables/" + variableId + "/formula")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.frecuenciaCaptura").exists());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void actualizarFormula_frecuenciaValida_siguePasandoAlServicio() throws Exception {
        UUID proyectoId = UUID.randomUUID();
        UUID variableId = UUID.randomUUID();
        ActualizarFormulaRequest req = new ActualizarFormulaRequest("ISE = A + B", null, "por_sprint");
        VariableDto dto = new VariableDto(variableId, proyectoId, UUID.randomUUID(), "M", "Cat",
                "V", "desc", "directo", "individual", "por_sprint", "1", "numerico",
                BigDecimal.ZERO, BigDecimal.TEN, true, Instant.now(),
                req.formulaTexto(), req.formulaJson(), req.frecuenciaCaptura(),
                null, null, null, null, null, null);
        when(variableService.actualizarFormula(anyString(), any(), any(), any())).thenReturn(dto);

        mockMvc.perform(patch("/api/proyectos/" + proyectoId + "/variables/" + variableId + "/formula")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────────
    // VariableDinamicaController / GuardarValoresRequest — @NotNull
    // ─────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "test@test.com")
    void guardarValores_proyectoIdNull_retorna400() throws Exception {
        UUID metricaId = UUID.randomUUID();
        String body = "{\"proyectoId\":null,\"sprintId\":\"" + UUID.randomUUID() + "\",\"valores\":[]}";

        mockMvc.perform(post("/api/metricas/" + metricaId + "/valores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.proyectoId").exists());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void guardarValores_variableIdNuloEnUnaFila_retorna400() throws Exception {
        UUID metricaId = UUID.randomUUID();
        String body = "{\"proyectoId\":\"" + UUID.randomUUID() + "\",\"sprintId\":\"" + UUID.randomUUID()
                + "\",\"valores\":[{\"variableId\":null,\"valorNum\":5}]}";

        mockMvc.perform(post("/api/metricas/" + metricaId + "/valores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void guardarValores_cuerpoValido_siguePasandoAlServicio() throws Exception {
        UUID metricaId = UUID.randomUUID();
        GuardarValoresRequest req = new GuardarValoresRequest(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new GuardarValoresRequest.ValorVariable(UUID.randomUUID(), BigDecimal.ONE,
                        null, null, null, null, null)));

        mockMvc.perform(post("/api/metricas/" + metricaId + "/valores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    // ─────────────────────────────────────────────────────────────────
    // MetricaAcademicaController / MetricaAcademicaRequest — @Size
    // ─────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "test@test.com")
    void generarPropuestaAcademica_codigoMetricaExcedeLimite_retorna400() throws Exception {
        String body = objectMapper.writeValueAsString(new MetricaAcademicaRequest(
                UUID.randomUUID(), UUID.randomUUID(), repetir("A", 21), "nombre",
                "definicion", "fuente", "SUMA(x)", "SUMA", "puntos", "por_sprint"));

        mockMvc.perform(post("/api/metricas-academicas/propuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.codigoMetrica").exists());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void generarPropuestaAcademica_formulaExcedeLimite_retorna400() throws Exception {
        String body = objectMapper.writeValueAsString(new MetricaAcademicaRequest(
                UUID.randomUUID(), UUID.randomUUID(), "COD", "nombre",
                "definicion", "fuente", repetir("x", 501), "SUMA", "puntos", "por_sprint"));

        mockMvc.perform(post("/api/metricas-academicas/propuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.formulaAcademica").exists());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void guardarPropuestaAcademica_proyectoIdNull_retorna400() throws Exception {
        String body = "{\"proyectoId\":null,\"metricaId\":\"" + UUID.randomUUID()
                + "\",\"formulaAcademica\":\"x\",\"tipoOperacion\":\"SUMA\"}";

        mockMvc.perform(post("/api/metricas-academicas/guardar-propuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────
    // MetricaIAController — @Size en necesidad/nombre/descripcion
    // ─────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "test@test.com")
    void generarPropuestaIA_necesidadExcedeLimite_retorna400() throws Exception {
        MetricaIAPropuestaRequest req = new MetricaIAPropuestaRequest(repetir("a", 4001));

        mockMvc.perform(post("/api/metricas-ia/propuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.necesidad").exists());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void generarPropuestaIA_necesidadVacia_retorna400() throws Exception {
        MetricaIAPropuestaRequest req = new MetricaIAPropuestaRequest("");

        mockMvc.perform(post("/api/metricas-ia/propuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void generarPropuestaIA_necesidadValida_siguePasandoAlServicio() throws Exception {
        MetricaIAPropuestaRequest req = new MetricaIAPropuestaRequest("Quiero medir el clima del equipo");
        MetricaIAPropuestaDto dto = new MetricaIAPropuestaDto("Clima", "desc", "obj", "que", "vars",
                "PROMEDIO", "PROMEDIO(x)", "puntos", "fuente");
        when(metricaIAService.generarPropuesta(anyString())).thenReturn(dto);

        mockMvc.perform(post("/api/metricas-ia/propuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void crearMetricaIA_nombreExcedeLimite_retorna400() throws Exception {
        CrearMetricaIARequest req = new CrearMetricaIARequest(UUID.randomUUID(), (short) 1,
                repetir("a", 121), "descripcion", null, null, null, null);

        mockMvc.perform(post("/api/metricas-ia/crear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void crearMetricaIA_descripcionExcedeLimite_retorna400() throws Exception {
        CrearMetricaIARequest req = new CrearMetricaIARequest(UUID.randomUUID(), (short) 1,
                "nombre válido", repetir("a", 4001), null, null, null, null);

        mockMvc.perform(post("/api/metricas-ia/crear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.descripcion").exists());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void crearMetricaIA_cuerpoValido_siguePasandoAlServicio() throws Exception {
        UUID proyectoId = UUID.randomUUID();
        CrearMetricaIARequest req = new CrearMetricaIARequest(proyectoId, (short) 1,
                "Clima del equipo", "Mide el clima del equipo", null, null, null, null);
        when(metricaIAService.crearDesdeConfirmacion(any()))
                .thenReturn(new MetricaIACreadaDto(UUID.randomUUID(), "IA-001", req.nombre(), proyectoId));

        mockMvc.perform(post("/api/metricas-ia/crear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    // ─────────────────────────────────────────────────────────────────
    // ParametrizacionController / ParametrizacionRequest — @NotBlank/@Size
    // ─────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "test@test.com")
    void generarPropuestas_metricaNombreVacio_retorna400() throws Exception {
        ParametrizacionRequest req = new ParametrizacionRequest("Factor", "Categoria", "", "Descripción");

        mockMvc.perform(post("/api/parametrizacion/propuestas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.metricaNombre").exists());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void generarPropuestas_metricaDescripcionExcedeLimite_retorna400() throws Exception {
        ParametrizacionRequest req = new ParametrizacionRequest("Factor", "Categoria", "Metrica", repetir("a", 4001));

        mockMvc.perform(post("/api/parametrizacion/propuestas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.metricaDescripcion").exists());
    }

    @Test
    @WithMockUser(username = "test@test.com")
    void generarPropuestas_cuerpoValido_siguePasandoAlServicio() throws Exception {
        ParametrizacionRequest req = new ParametrizacionRequest("Factor", "Categoria", "Metrica", "Descripción");
        when(parametrizacionService.generarPropuestas(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/parametrizacion/propuestas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // Nota: AsignarSprintHistoriaRequest.sprintId NO recibió ninguna anotación
    // nueva (sprintId=null es el mecanismo documentado para desasignar del
    // sprint) — no hay nada nuevo que regresar aquí. La cobertura existente
    // de HistoriaUsuarioControllerTest.asignarSprint_productOwner_permitido
    // ya prueba ese flujo contra el controller real, sin cambios.

    // ─────────────────────────────────────────────────────────────────
    // Los errores de validación no exponen internos (stack trace, SQL, etc.)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "test@test.com")
    void error400DeValidacion_noExponeStackTraceNiSqlNiPromptCompleto() throws Exception {
        MetricaIAPropuestaRequest req = new MetricaIAPropuestaRequest(repetir("SECRETO_QUE_NO_DEBE_APARECER_ENTERO", 200));

        String responseBody = mockMvc.perform(post("/api/metricas-ia/propuesta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(responseBody)
                .doesNotContain("com.prodox")
                .doesNotContain("java.lang")
                .doesNotContain("SELECT ")
                .doesNotContain("Exception")
                .doesNotContain(repetir("SECRETO_QUE_NO_DEBE_APARECER_ENTERO", 200));
    }
}
