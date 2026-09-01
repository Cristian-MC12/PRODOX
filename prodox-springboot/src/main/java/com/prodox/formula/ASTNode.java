// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.formula;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Nodo del Abstract Syntax Tree para fórmulas matemáticas.
 * Fase 16.8: Motor determinista de cálculo.
 */
public sealed interface ASTNode permits 
    ASTNode.NumberNode, 
    ASTNode.VariableNode, 
    ASTNode.BinaryOpNode,
    ASTNode.UnaryOpNode {
    
    /**
     * Evalúa el nodo con los valores de variables proporcionados.
     */
    BigDecimal evaluate(Map<UUID, BigDecimal> valores);
    
    /**
     * Nodo para constantes numéricas.
     */
    record NumberNode(BigDecimal value) implements ASTNode {
        @Override
        public BigDecimal evaluate(Map<UUID, BigDecimal> valores) {
            return value;
        }
    }
    
    /**
     * Nodo para variables identificadas por UUID.
     */
    record VariableNode(UUID variableId) implements ASTNode {
        @Override
        public BigDecimal evaluate(Map<UUID, BigDecimal> valores) {
            BigDecimal valor = valores.get(variableId);
            if (valor == null) {
                throw new IllegalArgumentException(
                    "Variable no encontrada o sin valor: " + variableId);
            }
            return valor;
        }
    }
    
    /**
     * Nodo para operaciones binarias (+, -, *, /).
     */
    record BinaryOpNode(ASTNode left, Operator op, ASTNode right) implements ASTNode {
        @Override
        public BigDecimal evaluate(Map<UUID, BigDecimal> valores) {
            BigDecimal leftVal = left.evaluate(valores);
            BigDecimal rightVal = right.evaluate(valores);
            
            return switch (op) {
                case ADD -> leftVal.add(rightVal);
                case SUBTRACT -> leftVal.subtract(rightVal);
                case MULTIPLY -> leftVal.multiply(rightVal);
                case DIVIDE -> {
                    if (rightVal.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ArithmeticException(
                            "División por cero: " + leftVal + " / " + rightVal);
                    }
                    yield leftVal.divide(rightVal, 4, java.math.RoundingMode.HALF_UP);
                }
            };
        }
    }
    
    /**
     * Nodo para operaciones unarias (-, +).
     */
    record UnaryOpNode(Operator op, ASTNode operand) implements ASTNode {
        @Override
        public BigDecimal evaluate(Map<UUID, BigDecimal> valores) {
            BigDecimal val = operand.evaluate(valores);
            return switch (op) {
                case SUBTRACT -> val.negate();
                case ADD -> val;
                default -> throw new IllegalStateException("Operador unario inválido: " + op);
            };
        }
    }
    
    /**
     * Operadores soportados.
     */
    enum Operator {
        ADD,        // +
        SUBTRACT,   // -
        MULTIPLY,   // *
        DIVIDE      // /
    }
}
