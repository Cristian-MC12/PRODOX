// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// Fase 16.9.1-B: Tests REST para MetricaAcademicaController
package com.mpdia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.*;
import com.mpdia.entity.MetricParametrizacion;
import com.mpdia.service.MetricaAcademicaService;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests REST para MetricaAcademicaController.
 * 
 * Valida:
 * - Endpoints REST
 * - Códigos HTTP correctos
 * - Seguridad (autenticación y autorización)
 * - Casos positivos y negativos
 * - Integración con servicio mockeado
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetricaAcademicaControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private MetricaAcademicaService service;
    
    private UUID metricaId;
    private UUID proyectoId;
    private UUID sprintId;
    private UUID resultadoId;
    
    @BeforeEach
    void setUp() {
        metricaId = UUID.randomUUID();
        proyectoId = UUID.randomUUID();
        sprintId = UUID.randomUUID();
        resultadoId = UUID.randomUUID();
    }
    
    // ========================================
    // Tests: Propuesta de Parametrización
    // ========================================
    
    @Test
    @WithMockUser(username = "test@test.com")
    void propuesta_casoValido_retorna200() throws Exception {
        // Arrange
        MetricaAcademicaRequest request = new MetricaAcademicaRequest(
            proyectoId,
            metricaId,
            "SIG-SC-02",
            "Problemas reportados por el cliente",
            "Total de problemas reportados por el cliente durante el sprint",
            "Guerrero-Calvache & Hernández (2024), p. 13, Tabla 9",
            "Σ problemas_reportados",
            "SUMA",
            "problemas",
            "por_sprint"
        );
        
        PropuestaParametrizacionDto propuesta = new PropuestaParametrizacionDto(
            "Parametrización SIG-SC-02",
            "Medir problemas reportados por el cliente",
            "Contar todos los problemas reportados durante el sprint",
            "problemas_reportados: INTEGER >= 0",
            "Numérica, valor entero no negativo",
            "por_sprint",
            "Guerrero-Calvache & Hernández (2024)",
            "Σ problemas_reportados",
            "SUMA",
            "problemas",
            "Basado en Guerrero-Calvache & Hernández (2024)",
            "problemas_reportados"
        , null, null, null, null, null, null);
        
        when(service.generarPropuestaAcademica(any(MetricaAcademicaRequest.class)))
            .thenReturn(propuesta);
        
        // Act & Assert
        mockMvc.perform(post("/api/metricas-academicas/propuesta")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.titulo").value("Parametrización SIG-SC-02"))
            .andExpect(jsonPath("$.objetivo").exists())
            .andExpect(jsonPath("$.procedimiento").exists())
            .andExpect(jsonPath("$.indicadorVariable").exists());
    }
    
    @Test
    void propuesta_sinAutenticacion_retorna401() throws Exception {
        // Arrange
        MetricaAcademicaRequest request = new MetricaAcademicaRequest(
            proyectoId,
            metricaId,
            "SIG-SC-02",
            "Problemas reportados",
            "Definición",
            "Fuente",
            "Σ problemas_reportados",
            "SUMA",
            "problemas",
            "por_sprint"
        );
        
        // Act & Assert
        mockMvc.perform(post("/api/metricas-academicas/propuesta")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }
    
    // ========================================
    // Tests: Guardar Propuesta
    // ========================================
    
    @Test
    @WithMockUser(username = "test@test.com")
    void guardarPropuesta_casoValido_retorna200() throws Exception {
        // Arrange
        GuardarPropuestaAcademicaRequest request = new GuardarPropuestaAcademicaRequest(
            proyectoId,
            metricaId,
            "Guerrero-Calvache & Hernández (2024), p. 13, Tabla 9",
            "Σ problemas_reportados",
            "SUMA",
            "problemas",
            "Medir problemas reportados",
            "Contar problemas del sprint",
            "problemas_reportados",
            "INTEGER >= 0",
            "por_sprint"
        , null, null, null, null, null, null);
        
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(UUID.randomUUID());
        parametrizacion.setVersion(1);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setFuenteAcademica(request.fuenteAcademica());
        parametrizacion.setFormulaAcademica(request.formulaAcademica());
        parametrizacion.setTipoOperacion(request.tipoOperacion());
        parametrizacion.setUnidadResultado(request.unidadResultado());
        
        when(service.guardarPropuestaAcademica(any(), any()))
            .thenReturn(parametrizacion);
        
        // Act & Assert
        mockMvc.perform(post("/api/metricas-academicas/guardar-propuesta")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.status").value("propuesta"))
            .andExpect(jsonPath("$.fuenteAcademica").exists())
            .andExpect(jsonPath("$.formulaAcademica").exists())
            .andExpect(jsonPath("$.tipoOperacion").value("SUMA"))
            .andExpect(jsonPath("$.unidadResultado").value("problemas"));
    }
    
    // ========================================
    // Tests: Ejecutar Métrica
    // ========================================
    
    @Test
    @WithMockUser(username = "test@test.com")
    void ejecutar_casoValido_retorna200ConResultado() throws Exception {
        // Arrange
        Map<String, Object> valores = Map.of("problemas_reportados", 7);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId,
            sprintId,
            valores
        );
        
        ResultadoMetricaDto resultado = new ResultadoMetricaDto(
            resultadoId,
            metricaId,
            "Problemas reportados por el cliente",
            proyectoId,
            sprintId,
            UUID.randomUUID(),
            1,
            "suma",
            "Σ problemas_reportados",
            "{\"problemas_reportados\": 7}",
            new BigDecimal("7"),
            "problemas",
            "calculado",
            null,
            Instant.now()
        );
        
        when(service.ejecutarMetricaAcademica(eq(metricaId), any(EjecutarMetricaAcademicaRequest.class)))
            .thenReturn(resultado);
        
        // Act & Assert
        mockMvc.perform(post("/api/metricas-academicas/{metricaId}/ejecutar", metricaId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultado").value(7))
            .andExpect(jsonPath("$.unidad").value("problemas"))
            .andExpect(jsonPath("$.tipoCalculo").value("suma"))
            .andExpect(jsonPath("$.expresion").value("Σ problemas_reportados"))
            .andExpect(jsonPath("$.parametrizacionVersion").value(1))
            .andExpect(jsonPath("$.estado").value("calculado"));
    }
    
    @Test
    @WithMockUser(username = "test@test.com")
    void ejecutar_sinParametrizacionAprobada_retorna409() throws Exception {
        // Arrange
        Map<String, Object> valores = Map.of("problemas_reportados", 7);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId,
            sprintId,
            valores
        );
        
        when(service.ejecutarMetricaAcademica(eq(metricaId), any()))
            .thenThrow(new IllegalStateException("No existe parametrización aprobada"));
        
        // Act & Assert
        mockMvc.perform(post("/api/metricas-academicas/{metricaId}/ejecutar", metricaId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());
    }
    
    @Test
    @WithMockUser(username = "test@test.com")
    void ejecutar_variableAusente_retorna400() throws Exception {
        // Arrange
        Map<String, Object> valores = Map.of("otra_variable", 7);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId,
            sprintId,
            valores
        );
        
        when(service.ejecutarMetricaAcademica(eq(metricaId), any()))
            .thenThrow(new IllegalArgumentException("Falta el valor para la variable"));
        
        // Act & Assert
        mockMvc.perform(post("/api/metricas-academicas/{metricaId}/ejecutar", metricaId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    @WithMockUser(username = "test@test.com")
    void ejecutar_valorNegativo_retorna400() throws Exception {
        // Arrange
        Map<String, Object> valores = Map.of("problemas_reportados", -5);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId,
            sprintId,
            valores
        );
        
        when(service.ejecutarMetricaAcademica(eq(metricaId), any()))
            .thenThrow(new IllegalArgumentException("El valor no puede ser negativo"));
        
        // Act & Assert
        mockMvc.perform(post("/api/metricas-academicas/{metricaId}/ejecutar", metricaId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    @WithMockUser(username = "test@test.com")
    void ejecutar_usuarioSinPermisos_retorna409() throws Exception {
        // Arrange
        Map<String, Object> valores = Map.of("problemas_reportados", 7);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId,
            sprintId,
            valores
        );
        
        when(service.ejecutarMetricaAcademica(eq(metricaId), any()))
            .thenThrow(new IllegalStateException("Usuario no pertenece al proyecto"));
        
        // Act & Assert
        mockMvc.perform(post("/api/metricas-academicas/{metricaId}/ejecutar", metricaId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());
    }
    
    @Test
    void ejecutar_sinAutenticacion_retorna401() throws Exception {
        // Arrange
        Map<String, Object> valores = Map.of("problemas_reportados", 7);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId,
            sprintId,
            valores
        );
        
        // Act & Assert
        mockMvc.perform(post("/api/metricas-academicas/{metricaId}/ejecutar", metricaId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }
    
    // ========================================
    // Tests: Histórico
    // ========================================
    
    @Test
    @WithMockUser(username = "test@test.com")
    void historico_casoValido_retornaListaOrdenada() throws Exception {
        // Arrange
        ResultadoMetricaDto r1 = crearResultado(new BigDecimal("5"), Instant.now().minusSeconds(100));
        ResultadoMetricaDto r2 = crearResultado(new BigDecimal("7"), Instant.now());
        
        when(service.obtenerHistorico(metricaId, proyectoId))
            .thenReturn(List.of(r2, r1)); // Orden descendente
        
        // Act & Assert
        mockMvc.perform(get("/api/metricas-academicas/{metricaId}/historico", metricaId)
                .param("proyectoId", proyectoId.toString())
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].resultado").value(7))
            .andExpect(jsonPath("$[1].resultado").value(5))
            .andExpect(jsonPath("$[0].parametrizacionVersion").exists())
            .andExpect(jsonPath("$[0].calculadoAt").exists());
    }
    
    @Test
    @WithMockUser(username = "test@test.com")
    void historico_vacio_retornaListaVacia() throws Exception {
        // Arrange
        when(service.obtenerHistorico(metricaId, proyectoId))
            .thenReturn(List.of());
        
        // Act & Assert
        mockMvc.perform(get("/api/metricas-academicas/{metricaId}/historico", metricaId)
                .param("proyectoId", proyectoId.toString())
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }
    
    @Test
    void historico_sinAutenticacion_retorna401() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/metricas-academicas/{metricaId}/historico", metricaId)
                .param("proyectoId", proyectoId.toString())
                .with(csrf()))
            .andExpect(status().isUnauthorized());
    }
    
    // ========================================
    // Tests: Interpretación IA
    // ========================================
    
    @Test
    @WithMockUser(username = "test@test.com")
    void interpretar_casoValido_retornaInterpretacion() throws Exception {
        // Arrange
        InterpretacionIADto interpretacion = new InterpretacionIADto(
            resultadoId,
            "Problemas reportados por el cliente",
            new BigDecimal("7"),
            "problemas",
            "El equipo registró 7 problemas reportados por el cliente en este sprint. " +
            "Esto representa un incremento respecto al sprint anterior...",
            Instant.now()
        );
        
        when(service.solicitarInterpretacionIA(resultadoId))
            .thenReturn(interpretacion);
        
        // Act & Assert
        mockMvc.perform(post("/api/metricas-academicas/resultados/{resultadoId}/interpretar", resultadoId)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultadoId").exists())
            .andExpect(jsonPath("$.metricaNombre").value("Problemas reportados por el cliente"))
            .andExpect(jsonPath("$.resultado").value(7))
            .andExpect(jsonPath("$.unidad").value("problemas"))
            .andExpect(jsonPath("$.interpretacion").exists())
            .andExpect(jsonPath("$.interpretacion").isNotEmpty());
    }
    
    @Test
    @WithMockUser(username = "test@test.com")
    void interpretar_resultadoInexistente_retorna400() throws Exception {
        // Arrange
        when(service.solicitarInterpretacionIA(resultadoId))
            .thenThrow(new IllegalArgumentException("Resultado no encontrado"));
        
        // Act & Assert
        mockMvc.perform(post("/api/metricas-academicas/resultados/{resultadoId}/interpretar", resultadoId)
                .with(csrf()))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    void interpretar_sinAutenticacion_retorna401() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/metricas-academicas/resultados/{resultadoId}/interpretar", resultadoId)
                .with(csrf()))
            .andExpect(status().isUnauthorized());
    }
    
    // ========================================
    // Métodos Auxiliares
    // ========================================
    
    private ResultadoMetricaDto crearResultado(BigDecimal valor, Instant fecha) {
        return new ResultadoMetricaDto(
            UUID.randomUUID(),
            metricaId,
            "Problemas reportados por el cliente",
            proyectoId,
            sprintId,
            UUID.randomUUID(),
            1,
            "suma",
            "Σ problemas_reportados",
            "{}",
            valor,
            "problemas",
            "calculado",
            null,
            fecha
        );
    }
}

/**
 * Record interno para request de guardar propuesta.
 * (Duplicado aquí para que el test compile independientemente)
 */
