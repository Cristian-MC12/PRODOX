// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.ai.gemini;

import java.util.List;

/**
 * Herramienta que agrupa funciones disponibles para Gemini.
 */
public record Tool(
    List<FunctionDeclaration> functionDeclarations
) {}
