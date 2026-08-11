// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto.ai;

import java.util.List;

/**
 * Respuesta del AI Agent después de procesar un mensaje.
 */
public record AgentResponse(
    String message,
    List<String> toolsUsed,
    Boolean hasData
) {}
