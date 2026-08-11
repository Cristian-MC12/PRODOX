// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.ai.AgentResponse;
import com.mpdia.dto.ai.gemini.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para AIAgentService.
 * No realizan llamadas reales a Gemini API (usa mocks).
 */
@ExtendWith(MockitoExtension.class)
class AIAgentServiceTest {

    @Mock
    private GeminiService geminiService;

    private AIAgentService aiAgentService;

    @BeforeEach
    void setUp() {
        aiAgentService = new AIAgentService(geminiService);
    }

    @Test
    void processSimpleMessage_sinTools_retornaRespuestaTexto() {
        // Given
        String userMessage = "Hola";
        String systemInstruction = "Eres un asistente útil";
        
        when(geminiService.chatWithTools(anyList(), isNull(), eq(systemInstruction)))
            .thenReturn(GeminiResponse.text("Hola! ¿En qué puedo ayudarte?"));

        // When
        AgentResponse response = aiAgentService.processSimpleMessage(userMessage, systemInstruction);

        // Then
        assertNotNull(response);
        assertEquals("Hola! ¿En qué puedo ayudarte?", response.message());
        assertTrue(response.toolsUsed().isEmpty());
        assertTrue(response.hasData());
    }

    @Test
    void processMessage_sinFunctionCall_retornaRespuestaDirecta() {
        // Given
        String userMessage = "¿Cuál es la capital de Francia?";
        List<Tool> tools = List.of();
        String systemInstruction = "Eres un asistente";

        when(geminiService.chatWithTools(anyList(), eq(tools), eq(systemInstruction)))
            .thenReturn(GeminiResponse.text("La capital de Francia es París"));

        ToolExecutor mockExecutor = (name, args) -> null;

        // When
        AgentResponse response = aiAgentService.processMessage(
            userMessage, tools, systemInstruction, mockExecutor
        );

        // Then
        assertNotNull(response);
        assertEquals("La capital de Francia es París", response.message());
        assertTrue(response.toolsUsed().isEmpty());
        assertTrue(response.hasData());
    }

    @Test
    void processMessage_conFunctionCall_ejecutaToolYRetornaRespuesta() {
        // Given
        String userMessage = "Obtén el resumen del proyecto xyz";
        
        FunctionDeclaration getProjectTool = new FunctionDeclaration(
            "getProjectOverview",
            "Obtiene resumen del proyecto",
            Map.of("type", "OBJECT", 
                   "properties", Map.of("proyectoId", Map.of("type", "STRING")),
                   "required", List.of("proyectoId"))
        );
        Tool tool = new Tool(List.of(getProjectTool));
        
        String systemInstruction = "Eres un AI Agile Copilot";

        // Mock del comportamiento de Gemini:
        // 1. Primera llamada: Gemini solicita function call
        FunctionCall functionCall = new FunctionCall(
            "getProjectOverview",
            Map.of("proyectoId", "proyecto-123")
        );
        
        // 2. Segunda llamada (después de ejecutar tool): Gemini retorna texto
        when(geminiService.chatWithTools(anyList(), anyList(), anyString()))
            .thenReturn(GeminiResponse.functionCall(functionCall))
            .thenReturn(GeminiResponse.text("El proyecto xyz tiene 5 sprints completados con productividad promedio de 82"));

        // Mock del tool executor
        ToolExecutor toolExecutor = (name, args) -> {
            if ("getProjectOverview".equals(name)) {
                return Map.of(
                    "proyectoNombre", "Proyecto XYZ",
                    "sprintsFinalizados", 5,
                    "promedioHistorico", Map.of("Productividad", 82.0)
                );
            }
            throw new IllegalArgumentException("Tool desconocida");
        };

        // When
        AgentResponse response = aiAgentService.processMessage(
            userMessage, List.of(tool), systemInstruction, toolExecutor
        );

        // Then
        assertNotNull(response);
        assertTrue(response.message().contains("82"));
        assertEquals(1, response.toolsUsed().size());
        assertEquals("getProjectOverview", response.toolsUsed().get(0));
        assertTrue(response.hasData());
        
        // Verificar que Gemini fue llamado 2 veces
        verify(geminiService, times(2)).chatWithTools(anyList(), anyList(), anyString());
    }

    @Test
    void processMessage_toolExecutorLanzaExcepcion_continuaYRetornaError() {
        // Given
        String userMessage = "Obtén datos del proyecto invalido";
        
        FunctionDeclaration tool = new FunctionDeclaration(
            "getProjectOverview",
            "Obtiene resumen del proyecto",
            Map.of()
        );
        
        FunctionCall functionCall = new FunctionCall(
            "getProjectOverview",
            Map.of("proyectoId", "invalid-id")
        );

        when(geminiService.chatWithTools(anyList(), anyList(), anyString()))
            .thenReturn(GeminiResponse.functionCall(functionCall))
            .thenReturn(GeminiResponse.text("Lo siento, hubo un error al obtener los datos del proyecto"));

        ToolExecutor failingExecutor = (name, args) -> {
            throw new IllegalArgumentException("Proyecto no encontrado");
        };

        // When
        AgentResponse response = aiAgentService.processMessage(
            userMessage, List.of(new Tool(List.of(tool))), "system", failingExecutor
        );

        // Then
        assertNotNull(response);
        assertTrue(response.message().contains("error") || response.message().contains("siento"));
    }

    @Test
    void processMessage_limiteIteraciones_retornaMensajeDeError() {
        // Given - Simular un loop infinito donde Gemini siempre pide function calls
        String userMessage = "Test";
        
        FunctionCall endlessCall = new FunctionCall("someTool", Map.of());
        when(geminiService.chatWithTools(anyList(), anyList(), anyString()))
            .thenReturn(GeminiResponse.functionCall(endlessCall));

        ToolExecutor mockExecutor = (name, args) -> Map.of("result", "ok");

        // When
        AgentResponse response = aiAgentService.processMessage(
            userMessage, List.of(), "system", mockExecutor
        );

        // Then
        assertNotNull(response);
        assertTrue(response.message().contains("no pude procesar"));
        assertFalse(response.hasData());
    }
}
