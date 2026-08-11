// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto.ai;

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
