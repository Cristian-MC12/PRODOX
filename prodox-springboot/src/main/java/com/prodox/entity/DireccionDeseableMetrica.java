// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.entity;

/**
 * Dirección deseable de una métrica para interpretación de tendencias.
 * 
 * Utilizado por AIInsightsService para generar contexto adecuado en prompts
 * de Gemini y evitar interpretaciones incorrectas (ej: aumento de "Defectos"
 * no debe clasificarse como positivo).
 * 
 * FASE: Prioridad 1 - Calidad analítica de módulos de IA
 */
public enum DireccionDeseableMetrica {
    
    /**
     * Valores mayores pueden ser favorables.
     * Ejemplo: Velocidad, Satisfacción del cliente, Cobertura de tests.
     * 
     * IMPORTANTE: "pueden ser" porque el contexto importa. Un aumento del 500%
     * en velocidad en un solo sprint puede indicar error de medición.
     */
    MAYOR_MEJOR,
    
    /**
     * Valores menores pueden ser favorables.
     * Ejemplo: Defectos, Tiempo de respuesta, Deuda técnica.
     * 
     * IMPORTANTE: "pueden ser" porque el contexto importa. Una reducción del
     * 100% puede ser simplemente falta de datos.
     */
    MENOR_MEJOR,
    
    /**
     * La métrica busca alcanzar un valor objetivo específico.
     * Ejemplo: Cobertura de código (objetivo: 80%), Puntos de historia (objetivo: 30).
     * 
     * IMPORTANTE: requiere que Metrica.valorObjetivo sea NOT NULL.
     * El análisis debe evaluar qué tan cerca está del objetivo, no solo si sube o baja.
     */
    OBJETIVO;
    
    /**
     * Convierte un String a DireccionDeseableMetrica.
     * 
     * @param valor String con el nombre del enum o null
     * @return DireccionDeseableMetrica correspondiente o null si valor es null
     * @throws IllegalArgumentException si el valor no es válido
     */
    public static DireccionDeseableMetrica fromString(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valueOf(valor.trim().toUpperCase());
    }
    
    /**
     * Verifica si esta dirección requiere un valor objetivo.
     * 
     * @return true si direccion=OBJETIVO, false en caso contrario
     */
    public boolean requiereValorObjetivo() {
        return this == OBJETIVO;
    }
    
    /**
     * Verifica si el valor objetivo debe ser NULL para esta dirección.
     * 
     * @return true si direccion IN (MAYOR_MEJOR, MENOR_MEJOR), false si es OBJETIVO
     */
    public boolean valorObjetivoDebeSerNull() {
        return this != OBJETIVO;
    }
}
