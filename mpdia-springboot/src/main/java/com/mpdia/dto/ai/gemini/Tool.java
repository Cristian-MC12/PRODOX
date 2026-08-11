// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto.ai.gemini;

import java.util.List;

/**
 * Herramienta que agrupa funciones disponibles para Gemini.
 */
public record Tool(
    List<FunctionDeclaration> functionDeclarations
) {}
