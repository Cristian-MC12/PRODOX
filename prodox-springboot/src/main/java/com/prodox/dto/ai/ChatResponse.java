// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.ai;

import java.time.Instant;
import java.util.List;

/**
 * Response del AI Copilot al usuario.
 */
public record ChatResponse(
    String message,
    List<String> toolsUsed,
    Instant timestamp,
    Boolean hasData
) {}
