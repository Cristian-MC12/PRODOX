// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.ai;

import java.util.List;

/**
 * Respuesta del AI Agent después de procesar un mensaje.
 */
public record AgentResponse(
    String message,
    List<String> toolsUsed,
    Boolean hasData
) {}
