// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.formula;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FormulaEvaluatorTest {
    
    private FormulaEvaluator evaluator;
    
    @BeforeEach
    void setUp() {
        evaluator = new FormulaEvaluator();
    }
    
    @Test
    void evaluarDirecto_conValor_retornaValor() {
        UUID varId = UUID.randomUUID();
        Map<UUID, BigDecimal> valores = Map.of(varId, new BigDecimal("42.5"));
        
        BigDecimal resultado = evaluator.evaluarDirecto(varId, valores);
        
        assertEquals(new BigDecimal("42.5000"), resultado);
    }
    
    @Test
    void evaluarDirecto_sinValor_lanzaExcepcion() {
        UUID varId = UUID.randomUUID();
        Map<UUID, BigDecimal> valores = Map.of();
        
        assertThrows(IllegalArgumentException.class, () -> {
            evaluator.evaluarDirecto(varId, valores);
        });
    }
    
    @Test
    void evaluarSuma_variosValores_retornaSuma() {
        UUID varId = UUID.randomUUID();
        List<BigDecimal> valores = List.of(
            new BigDecimal("10"),
            new BigDecimal("20"),
            new BigDecimal("30")
        );
        
        BigDecimal resultado = evaluator.evaluarSuma(varId, valores);
        
        assertEquals(new BigDecimal("60.0000"), resultado);
    }
    
    @Test
    void evaluarSuma_listaVacia_lanzaExcepcion() {
        UUID varId = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            evaluator.evaluarSuma(varId, List.of());
        });
    }
    
    @Test
    void evaluarPromedio_variosValores_retornaPromedio() {
        UUID varId = UUID.randomUUID();
        List<BigDecimal> valores = List.of(
            new BigDecimal("4"),
            new BigDecimal("5"),
            new BigDecimal("3")
        );
        
        BigDecimal resultado = evaluator.evaluarPromedio(varId, valores);
        
        // (4 + 5 + 3) / 3 = 4
        assertEquals(new BigDecimal("4.0000"), resultado);
    }
    
    @Test
    void evaluarPromedio_listaVacia_lanzaExcepcion() {
        UUID varId = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> {
            evaluator.evaluarPromedio(varId, List.of());
        });
    }
    
    @Test
    void evaluarFormula_suma_funciona() {
        UUID varA = UUID.randomUUID();
        UUID varB = UUID.randomUUID();
        String expresion = "${" + varA + "} + ${" + varB + "}";
        
        Map<UUID, BigDecimal> valores = Map.of(
            varA, new BigDecimal("42"),
            varB, new BigDecimal("8")
        );
        
        BigDecimal resultado = evaluator.evaluarFormula(expresion, valores);
        
        assertEquals(new BigDecimal("50.0000"), resultado);
    }
    
    @Test
    void evaluarFormula_divisionPorCero_lanzaExcepcion() {
        UUID varA = UUID.randomUUID();
        UUID varB = UUID.randomUUID();
        String expresion = "${" + varA + "} / ${" + varB + "}";
        
        Map<UUID, BigDecimal> valores = Map.of(
            varA, new BigDecimal("42"),
            varB, BigDecimal.ZERO
        );
        
        assertThrows(ArithmeticException.class, () -> {
            evaluator.evaluarFormula(expresion, valores);
        });
    }
    
    @Test
    void evaluarFormula_variableFaltante_lanzaExcepcion() {
        UUID varA = UUID.randomUUID();
        UUID varB = UUID.randomUUID();
        String expresion = "${" + varA + "} + ${" + varB + "}";
        
        Map<UUID, BigDecimal> valores = Map.of(
            varA, new BigDecimal("42")
            // varB falta
        );
        
        assertThrows(IllegalArgumentException.class, () -> {
            evaluator.evaluarFormula(expresion, valores);
        });
    }
    
    @Test
    void evaluarFormula_velocity_funciona() {
        // Velocity = completados / planificados * 100
        UUID completados = UUID.randomUUID();
        UUID planificados = UUID.randomUUID();
        String expresion = "${" + completados + "} / ${" + planificados + "} * 100";
        
        Map<UUID, BigDecimal> valores = Map.of(
            completados, new BigDecimal("42"),
            planificados, new BigDecimal("50")
        );
        
        BigDecimal resultado = evaluator.evaluarFormula(expresion, valores);
        
        // 42 / 50 * 100 = 84
        assertEquals(new BigDecimal("84.0000"), resultado);
    }
    
    @Test
    void evaluarFormula_precedencia_respetada() {
        UUID varA = UUID.randomUUID();
        String expresion = "2 + ${" + varA + "} * 4";
        
        Map<UUID, BigDecimal> valores = Map.of(varA, new BigDecimal("3"));
        
        BigDecimal resultado = evaluator.evaluarFormula(expresion, valores);
        
        // 2 + 3 * 4 = 14 (no 20)
        assertEquals(new BigDecimal("14.0000"), resultado);
    }
    
    @Test
    void validarExpresion_valida_noLanzaExcepcion() {
        UUID varId = UUID.randomUUID();
        String expresion = "${" + varId + "} * 100";
        
        assertDoesNotThrow(() -> {
            evaluator.validarExpresion(expresion);
        });
    }
    
    @Test
    void validarExpresion_invalida_lanzaExcepcion() {
        String expresion = "2 +"; // Expresión incompleta
        
        assertThrows(IllegalArgumentException.class, () -> {
            evaluator.validarExpresion(expresion);
        });
    }
}
