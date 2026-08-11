package com.mpdia.controller;

import com.mpdia.dto.ai.ChatRequest;
import com.mpdia.dto.ai.ChatResponse;
import com.mpdia.ratelimit.RateLimitService;
import com.mpdia.service.AICopilotService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del AICopilotController.
 * 
 * Usa @SpringBootTest con @AutoConfigureMockMvc para tests del controller.
 * Mockea AICopilotService para no depender de servicios externos (Gemini).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AICopilotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AICopilotService copilotService;

    @Autowired
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        // Limpiar rate limit antes de cada test
        rateLimitService.resetAll();
    }

    @Test
    @WithMockUser(username = "user123")
    void chat_casoExitoso() throws Exception {
        // Given
        UUID proyectoId = UUID.randomUUID();
        ChatRequest request = new ChatRequest(
                "¿Cuáles son las métricas del sprint activo?",
                proyectoId,
                null
        );

        ChatResponse mockResponse = new ChatResponse(
                "El sprint activo tiene 5 métricas configuradas...",
                List.of("getActiveSprintMetrics"),
                Instant.now(),
                true
        );

        when(copilotService.chat(any(ChatRequest.class), eq("user123")))
                .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/ai/copilot/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("El sprint activo tiene 5 métricas configuradas..."))
                .andExpect(jsonPath("$.toolsUsed[0]").value("getActiveSprintMetrics"))
                .andExpect(jsonPath("$.hasData").value(true));
    }

    @Test
    @WithMockUser(username = "user123")
    void chat_mensajeVacio_retorna400() throws Exception {
        // Given
        UUID proyectoId = UUID.randomUUID();
        ChatRequest request = new ChatRequest(
                "",  // mensaje vacío
                proyectoId,
                null
        );

        // When & Then
        mockMvc.perform(post("/api/ai/copilot/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user123")
    void chat_proyectoIdNulo_retorna400() throws Exception {
        // Given - crear el JSON manualmente para evitar validación del constructor
        String requestJson = "{\"message\": \"test\", \"proyectoId\": null}";

        // When & Then
        mockMvc.perform(post("/api/ai/copilot/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_sinAutenticacion_retorna403() throws Exception {
        // Given
        UUID proyectoId = UUID.randomUUID();
        ChatRequest request = new ChatRequest(
                "¿Cuáles son las métricas?",
                proyectoId,
                null
        );

        // When & Then
        // Sin autenticación, Spring Security retorna 403 (no 401)
        mockMvc.perform(post("/api/ai/copilot/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user123")
    void chat_proyectoNoAutorizado_retorna403() throws Exception {
        // Given
        UUID proyectoId = UUID.randomUUID();
        ChatRequest request = new ChatRequest(
                "¿Cuáles son las métricas?",
                proyectoId,
                null
        );

        when(copilotService.chat(any(ChatRequest.class), eq("user123")))
                .thenThrow(new SecurityException("No tienes acceso a este proyecto"));

        // When & Then
        mockMvc.perform(post("/api/ai/copilot/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user123")
    void chat_proyectoInexistente_retorna400() throws Exception {
        // Given
        UUID proyectoId = UUID.randomUUID();
        ChatRequest request = new ChatRequest(
                "¿Cuáles son las métricas?",
                proyectoId,
                null
        );

        when(copilotService.chat(any(ChatRequest.class), eq("user123")))
                .thenThrow(new IllegalArgumentException("Proyecto no encontrado"));

        // When & Then
        mockMvc.perform(post("/api/ai/copilot/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user123")
    void chat_mensajeMuyLargo_retorna400() throws Exception {
        // Given
        UUID proyectoId = UUID.randomUUID();
        String mensajeLargo = "a".repeat(4001); // excede el límite de 4000
        ChatRequest request = new ChatRequest(
                mensajeLargo,
                proyectoId,
                null
        );

        // When & Then
        mockMvc.perform(post("/api/ai/copilot/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user123")
    void chat_sprintNoPertenece_retorna403() throws Exception {
        // Given
        UUID proyectoId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        ChatRequest request = new ChatRequest(
                "¿Cuáles son las métricas?",
                proyectoId,
                sprintId
        );

        when(copilotService.chat(any(ChatRequest.class), eq("user123")))
                .thenThrow(new SecurityException("El sprint no pertenece a este proyecto"));

        // When & Then
        mockMvc.perform(post("/api/ai/copilot/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user123")
    void chat_excedeRateLimit_retorna429() throws Exception {
        // Given
        UUID proyectoId = UUID.randomUUID();
        ChatRequest request = new ChatRequest(
                "¿Cuáles son las métricas?",
                proyectoId,
                null
        );

        ChatResponse mockResponse = new ChatResponse(
                "Respuesta del AI",
                List.of(),
                Instant.now(),
                true
        );

        when(copilotService.chat(any(ChatRequest.class), eq("user123")))
                .thenReturn(mockResponse);

        // Configurar límite bajo para test (límite default en test profile es 10)
        // Hacer 10 requests exitosos
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/ai/copilot/chat")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // When - el request 11 debe ser rechazado
        mockMvc.perform(post("/api/ai/copilot/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("límite")));
    }

    @Test
    @WithMockUser(username = "user123")
    void chat_rateLimitIndependientePorUsuario() throws Exception {
        // Given
        UUID proyectoId = UUID.randomUUID();
        ChatRequest request = new ChatRequest(
                "¿Cuáles son las métricas?",
                proyectoId,
                null
        );

        ChatResponse mockResponse = new ChatResponse(
                "Respuesta del AI",
                List.of(),
                Instant.now(),
                true
        );

        when(copilotService.chat(any(ChatRequest.class), any(String.class)))
                .thenReturn(mockResponse);

        // When - user123 llega al límite (10 requests)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/ai/copilot/chat")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // user123 es rechazado
        mockMvc.perform(post("/api/ai/copilot/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());

        // Then - user456 puede hacer requests (límite independiente)
        mockMvc.perform(post("/api/ai/copilot/chat")
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("user456").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user123")
    void chat_cambiarProyecto_noReiniciaContador() throws Exception {
        // Given
        UUID proyecto1 = UUID.randomUUID();
        UUID proyecto2 = UUID.randomUUID();
        
        ChatResponse mockResponse = new ChatResponse(
                "Respuesta",
                List.of(),
                Instant.now(),
                true
        );

        when(copilotService.chat(any(ChatRequest.class), eq("user123")))
                .thenReturn(mockResponse);

        // When - hacer 5 requests a proyecto1
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/ai/copilot/chat")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                new ChatRequest("test", proyecto1, null))))
                    .andExpect(status().isOk());
        }

        // Luego hacer 5 requests a proyecto2
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/ai/copilot/chat")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                new ChatRequest("test", proyecto2, null))))
                    .andExpect(status().isOk());
        }

        // Then - el request 11 (mismo user, proyecto diferente) debe ser rechazado
        mockMvc.perform(post("/api/ai/copilot/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                            new ChatRequest("test", proyecto1, null))))
                .andExpect(status().isTooManyRequests());
    }
}
