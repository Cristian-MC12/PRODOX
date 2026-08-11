package com.mpdia.integration;

import com.mpdia.dto.ai.ChatRequest;
import com.mpdia.dto.ai.ChatResponse;
import com.mpdia.dto.SprintDto;
import com.mpdia.entity.Proyecto;
import com.mpdia.entity.ProjectMember;
import com.mpdia.entity.Sprint;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.ProyectoRepository;
import com.mpdia.repository.SprintRepository;
import com.mpdia.service.AICopilotService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Prueba de integración MANUAL del AI Copilot contra Gemini API REAL.
 * 
 * IMPORTANTE:
 * - Esta prueba está @Disabled para que NO se ejecute automáticamente con mvn test
 * - Para ejecutarla manualmente, quitar @Disabled temporalmente
 * - Requiere API key válida de Gemini en application.properties
 * - Requiere base de datos con datos reales de MPDIA
 * - Solo READ-ONLY, no modifica datos
 * 
 * EJECUCIÓN MANUAL:
 * 1. Verificar que mpdia.gemini.api-key está configurada
 * 2. Verificar que hay al menos un proyecto con sprint activo en BD
 * 3. Quitar @Disabled temporalmente
 * 4. Ejecutar solo este test: mvn test -Dtest=AICopilotIntegrationTest
 * 5. Volver a agregar @Disabled
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Disabled("Prueba manual de integración - ejecutar explícitamente solo cuando sea necesario")
public class AICopilotIntegrationTest {

    @Autowired
    private AICopilotService copilotService;

    @Autowired
    private ProyectoRepository proyectoRepository;

    @Autowired
    private SprintRepository sprintRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Test
    public void testAICopilotIntegrationWithRealGemini() {
        log.info("=".repeat(80));
        log.info("INICIANDO PRUEBA DE INTEGRACIÓN DEL AI COPILOT");
        log.info("=".repeat(80));

        long startTime = System.currentTimeMillis();
        String testResult = "UNKNOWN";
        String geminiStatus = "NOT_TESTED";
        String functionCallingStatus = "NOT_TESTED";
        String toolExecuted = "NONE";
        String authorizationStatus = "NOT_TESTED";
        String dataAvailability = "NOT_TESTED";
        String finalResponse = "";
        String errors = "";

        try {
            // PASO 1: Verificar datos reales en BD
            log.info("\n[1/9] Verificando datos reales en BD...");
            Optional<Proyecto> proyectoOpt = proyectoRepository.findAll().stream().findFirst();
            
            if (proyectoOpt.isEmpty()) {
                errors = "No hay proyectos en la base de datos";
                dataAvailability = "NOT_AVAILABLE";
                log.error("ERROR: {}", errors);
                printResults(geminiStatus, functionCallingStatus, toolExecuted, authorizationStatus, 
                           dataAvailability, finalResponse, errors, "FAILED", startTime);
                return;
            }

            Proyecto proyecto = proyectoOpt.get();
            UUID proyectoId = proyecto.getId();
            log.info("✓ Proyecto encontrado: {} (ID: {})", proyecto.getNombre(), proyectoId);

            // Verificar que existe un usuario miembro del proyecto
            Optional<ProjectMember> memberOpt = projectMemberRepository
                    .findByProyectoId(proyectoId).stream().findFirst();
            
            if (memberOpt.isEmpty()) {
                errors = "No hay miembros en el proyecto";
                authorizationStatus = "NO_MEMBERS";
                log.error("ERROR: {}", errors);
                printResults(geminiStatus, functionCallingStatus, toolExecuted, authorizationStatus, 
                           dataAvailability, finalResponse, errors, "FAILED", startTime);
                return;
            }

            String userId = memberOpt.get().getUserId();
            log.info("✓ Usuario miembro encontrado: {}", userId);

            // Verificar que existe un sprint activo
            Optional<Sprint> sprintOpt = sprintRepository
                    .findByProyectoIdAndEstado(proyectoId, "ejecucion");
            
            if (sprintOpt.isEmpty()) {
                log.warn("⚠ No hay sprint activo, buscando cualquier sprint...");
                sprintOpt = sprintRepository.findTopByProyectoIdOrderByNumeroDesc(proyectoId);
            }

            if (sprintOpt.isEmpty()) {
                errors = "No hay sprints en el proyecto";
                dataAvailability = "NO_SPRINTS";
                log.error("ERROR: {}", errors);
                printResults(geminiStatus, functionCallingStatus, toolExecuted, authorizationStatus, 
                           dataAvailability, finalResponse, errors, "FAILED", startTime);
                return;
            }

            Sprint sprint = sprintOpt.get();
            log.info("✓ Sprint encontrado: Sprint {} (Estado: {})", sprint.getNumero(), sprint.getEstado());
            dataAvailability = "AVAILABLE";

            // PASO 2: Preparar consulta al AI Copilot
            log.info("\n[2/9] Preparando consulta al AI Copilot...");
            ChatRequest request = new ChatRequest(
                    "Analiza el sprint activo del proyecto que tengo seleccionado y dime las principales métricas disponibles.",
                    proyectoId,
                    sprintOpt.map(Sprint::getId).orElse(null) // sprintId opcional
            );
            log.info("✓ Consulta preparada");

            // PASO 3: Ejecutar consulta contra Gemini REAL
            log.info("\n[3/9] Ejecutando consulta contra Gemini API REAL...");
            log.info("NOTA: Esta llamada va a Gemini API con tu API key real");
            
            ChatResponse response = copilotService.chat(request, userId);
            
            geminiStatus = "CONNECTED";
            log.info("✓ Gemini respondió exitosamente");

            // PASO 4: Verificar que hubo function calling
            log.info("\n[4/9] Verificando function calling...");
            if (response.message().contains("sprint") || response.message().contains("métrica") || 
                response.message().contains("dato")) {
                functionCallingStatus = "WORKING";
                log.info("✓ Function calling parece haber funcionado (respuesta contiene datos relevantes)");
                
                // Verificar qué tools se ejecutaron realmente
                if (response.toolsUsed() != null && !response.toolsUsed().isEmpty()) {
                    toolExecuted = String.join(", ", response.toolsUsed());
                } else if (response.message().toLowerCase().contains("activo") || 
                    response.message().toLowerCase().contains("ejecución")) {
                    toolExecuted = "getActiveSprintMetrics (inferido)";
                } else if (response.message().toLowerCase().contains("proyecto")) {
                    toolExecuted = "getProjectOverview o getProjectDetails (inferido)";
                }
            } else {
                functionCallingStatus = "UNCLEAR";
                log.warn("⚠ No está claro si function calling funcionó correctamente");
            }

            // PASO 5: Verificar autorización
            log.info("\n[5/9] Verificando autorización...");
            authorizationStatus = "PASSED";
            log.info("✓ Autorización funcionó (no hubo SecurityException)");

            // PASO 6-9: Registrar respuesta final
            log.info("\n[6-9] Respuesta final de Gemini:");
            finalResponse = response.message();
            log.info("─".repeat(80));
            log.info(finalResponse);
            log.info("─".repeat(80));

            testResult = "PASSED";

        } catch (SecurityException e) {
            geminiStatus = "ERROR";
            authorizationStatus = "FAILED";
            errors = "SecurityException: " + e.getMessage();
            testResult = "FAILED";
            log.error("ERROR DE AUTORIZACIÓN: {}", e.getMessage());

        } catch (Exception e) {
            geminiStatus = "FAILED";
            errors = e.getClass().getSimpleName() + ": " + e.getMessage();
            testResult = "FAILED";
            log.error("ERROR EN PRUEBA DE INTEGRACIÓN: {}", e.getMessage(), e);
        }

        // Imprimir resultados finales
        printResults(geminiStatus, functionCallingStatus, toolExecuted, authorizationStatus, 
                   dataAvailability, finalResponse, errors, testResult, startTime);
    }

    private void printResults(String geminiStatus, String functionCallingStatus, String toolExecuted,
                             String authorizationStatus, String dataAvailability, String finalResponse,
                             String errors, String testResult, long startTime) {
        
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("\n");
        log.info("=".repeat(80));
        log.info("RESULTADOS DE LA PRUEBA DE INTEGRACIÓN");
        log.info("=".repeat(80));
        log.info("");
        log.info("### Gemini");
        log.info("Status: {}", geminiStatus);
        log.info("");
        log.info("### Function Calling");
        log.info("Status: {}", functionCallingStatus);
        log.info("");
        log.info("### Tool");
        log.info("Tool ejecutada: {}", toolExecuted);
        log.info("");
        log.info("### Authorization");
        log.info("Status: {}", authorizationStatus);
        log.info("");
        log.info("### Real MPDIA data");
        log.info("Status: {}", dataAvailability);
        log.info("");
        log.info("### Final response");
        if (!finalResponse.isEmpty()) {
            log.info("Respuesta:");
            log.info("─".repeat(80));
            log.info(finalResponse);
            log.info("─".repeat(80));
        } else {
            log.info("(Sin respuesta)");
        }
        log.info("");
        log.info("### Errores");
        if (errors.isEmpty()) {
            log.info("Ninguno");
        } else {
            log.info("Error: {}", errors);
        }
        log.info("");
        log.info("### Duración");
        log.info("{}ms ({}s)", duration, duration / 1000.0);
        log.info("");
        log.info("### Conclusión");
        log.info("INTEGRATION TEST: {}", testResult);
        log.info("");
        log.info("=".repeat(80));
    }
}
