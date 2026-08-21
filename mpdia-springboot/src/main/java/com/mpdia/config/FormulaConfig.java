// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.config;

import com.mpdia.formula.FormulaEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración para el motor de fórmulas.
 * Fase 16.8: Motor determinista de cálculo.
 */
@Configuration
public class FormulaConfig {
    
    @Bean
    public FormulaEvaluator formulaEvaluator() {
        return new FormulaEvaluator();
    }
}
