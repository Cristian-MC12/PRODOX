// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.formula;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evaluador de fórmulas matemáticas con soporte para diferentes tipos.
 * Fase 16.8: Motor determinista de cálculo de métricas.
 * 
 * Tipos soportados:
 * - DIRECTO: valor registrado sin cálculo
 * - SUMA: suma de valores
 * - PROMEDIO: promedio de valores
 * - FORMULA: expresión aritmética evaluada con AST
 * 
 * NO utiliza Gemini, eval(), ScriptEngine ni ejecución arbitraria de código.
 */
public class FormulaEvaluator {
    
    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    
    /**
     * Evalúa una fórmula de tipo DIRECTO.
     * Retorna el valor registrado sin transformación.
     */
    public BigDecimal evaluarDirecto(UUID variableId, Map<UUID, BigDecimal> valores) {
        BigDecimal valor = valores.get(variableId);
        if (valor == null) {
            throw new IllegalArgumentException(
                "No hay valor registrado para la variable: " + variableId);
        }
        return valor.setScale(SCALE, ROUNDING);
    }
    
    /**
     * Evalúa una fórmula de tipo SUMA.
     * Suma todos los valores de una variable.
     */
    public BigDecimal evaluarSuma(UUID variableId, List<BigDecimal> valores) {
        if (valores == null || valores.isEmpty()) {
            throw new IllegalArgumentException(
                "No hay valores para sumar de la variable: " + variableId);
        }
        
        BigDecimal suma = BigDecimal.ZERO;
        for (BigDecimal valor : valores) {
            if (valor != null) {
                suma = suma.add(valor);
            }
        }
        
        return suma.setScale(SCALE, ROUNDING);
    }
    
    /**
     * Evalúa una fórmula de tipo PROMEDIO.
     * Calcula el promedio de valores de una variable.
     */
    public BigDecimal evaluarPromedio(UUID variableId, List<BigDecimal> valores) {
        if (valores == null || valores.isEmpty()) {
            throw new IllegalArgumentException(
                "No hay valores para promediar de la variable: " + variableId);
        }
        
        // Filtrar nulls
        List<BigDecimal> valoresNoNulos = valores.stream()
            .filter(v -> v != null)
            .toList();
        
        if (valoresNoNulos.isEmpty()) {
            throw new IllegalArgumentException(
                "Todos los valores son nulos para la variable: " + variableId);
        }
        
        BigDecimal suma = BigDecimal.ZERO;
        for (BigDecimal valor : valoresNoNulos) {
            suma = suma.add(valor);
        }
        
        BigDecimal count = new BigDecimal(valoresNoNulos.size());
        return suma.divide(count, SCALE, ROUNDING);
    }
    
    /**
     * Evalúa una fórmula de tipo FORMULA.
     * Parsea la expresión y la evalúa con el AST.
     * 
     * @param expresion Expresión aritmética con variables ${uuid}
     * @param valores   Mapa de UUID → valor para resolver variables
     * @return Resultado del cálculo
     * @throws IllegalArgumentException si la expresión es inválida
     * @throws ArithmeticException si hay división por cero
     */
    public BigDecimal evaluarFormula(String expresion, Map<UUID, BigDecimal> valores) {
        if (expresion == null || expresion.isBlank()) {
            throw new IllegalArgumentException("Expresión vacía");
        }
        
        if (valores == null || valores.isEmpty()) {
            throw new IllegalArgumentException("No hay valores para resolver variables");
        }
        
        try {
            // Tokenizar
            FormulaTokenizer tokenizer = new FormulaTokenizer(expresion);
            List<Token> tokens = tokenizer.tokenize();
            
            // Parsear
            FormulaParser parser = new FormulaParser(tokens);
            ASTNode ast = parser.parse();
            
            // Evaluar
            BigDecimal resultado = ast.evaluate(valores);
            
            return resultado.setScale(SCALE, ROUNDING);
            
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Error evaluando fórmula: " + e.getMessage(), e);
        } catch (ArithmeticException e) {
            throw new ArithmeticException(
                "Error aritmético en fórmula: " + e.getMessage());
        }
    }
    
    /**
     * Valida que una expresión sea sintácticamente correcta sin evaluarla.
     */
    public void validarExpresion(String expresion) {
        if (expresion == null || expresion.isBlank()) {
            throw new IllegalArgumentException("Expresión vacía");
        }
        
        try {
            FormulaTokenizer tokenizer = new FormulaTokenizer(expresion);
            List<Token> tokens = tokenizer.tokenize();
            
            FormulaParser parser = new FormulaParser(tokens);
            parser.parse(); // Solo valida sintaxis, no evalúa
            
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Expresión inválida: " + e.getMessage(), e);
        }
    }
}
