// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import java.util.Map;

/**
 * Interface funcional para ejecutar tools solicitadas por el AI Agent.
 * Permite que AIAgentService sea agnóstico de la lógica específica de tools.
 */
@FunctionalInterface
public interface ToolExecutor {
    /**
     * Ejecuta una tool específica con los argumentos provistos.
     * 
     * @param toolName Nombre de la tool a ejecutar
     * @param args Argumentos para la tool
     * @return Resultado de la ejecución (será serializado a JSON para enviar a la IA)
     * @throws IllegalArgumentException si la tool no existe
     * @throws SecurityException si hay problemas de autorización
     */
    Object execute(String toolName, Map<String, Object> args);
}
