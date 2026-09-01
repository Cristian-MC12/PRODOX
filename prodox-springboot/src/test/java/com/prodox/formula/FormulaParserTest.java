// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.formula;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FormulaParserTest {
    
    @Test
    void parsear_numero_retornaNumberNode() {
        List<Token> tokens = new FormulaTokenizer("42").tokenize();
        ASTNode ast = new FormulaParser(tokens).parse();
        
        BigDecimal resultado = ast.evaluate(Map.of());
        assertEquals(new BigDecimal("42"), resultado);
    }
    
    @Test
    void parsear_suma_retornaBinaryOpNode() {
        List<Token> tokens = new FormulaTokenizer("42 + 8").tokenize();
        ASTNode ast = new FormulaParser(tokens).parse();
        
        BigDecimal resultado = ast.evaluate(Map.of());
        assertEquals(new BigDecimal("50"), resultado);
    }
    
    @Test
    void parsear_resta_funciona() {
        List<Token> tokens = new FormulaTokenizer("10 - 3").tokenize();
        ASTNode ast = new FormulaParser(tokens).parse();
        
        assertEquals(new BigDecimal("7"), ast.evaluate(Map.of()));
    }
    
    @Test
    void parsear_multiplicacion_funciona() {
        List<Token> tokens = new FormulaTokenizer("4 * 5").tokenize();
        ASTNode ast = new FormulaParser(tokens).parse();
        
        assertEquals(new BigDecimal("20"), ast.evaluate(Map.of()));
    }
    
    @Test
    void parsear_division_funciona() {
        List<Token> tokens = new FormulaTokenizer("20 / 4").tokenize();
        ASTNode ast = new FormulaParser(tokens).parse();
        
        assertEquals(new BigDecimal("5.0000"), ast.evaluate(Map.of()));
    }
    
    @Test
    void parsear_precedencia_multiplicacionAntesQueSuma() {
        // 2 + 3 * 4 = 14 (no 20)
        List<Token> tokens = new FormulaTokenizer("2 + 3 * 4").tokenize();
        ASTNode ast = new FormulaParser(tokens).parse();
        
        assertEquals(new BigDecimal("14"), ast.evaluate(Map.of()));
    }
    
    @Test
    void parsear_parentesis_alteraPrecedencia() {
        // (2 + 3) * 4 = 20 (no 14)
        List<Token> tokens = new FormulaTokenizer("(2 + 3) * 4").tokenize();
        ASTNode ast = new FormulaParser(tokens).parse();
        
        assertEquals(new BigDecimal("20"), ast.evaluate(Map.of()));
    }
    
    @Test
    void parsear_numeroDecimal_funciona() {
        List<Token> tokens = new FormulaTokenizer("3.14 + 2.86").tokenize();
        ASTNode ast = new FormulaParser(tokens).parse();
        
        assertEquals(new BigDecimal("6.00"), ast.evaluate(Map.of()));
    }
    
    @Test
    void parsear_variable_retornaVariableNode() {
        UUID varId = UUID.randomUUID();
        String expresion = "${" + varId + "}";
        
        List<Token> tokens = new FormulaTokenizer(expresion).tokenize();
        ASTNode ast = new FormulaParser(tokens).parse();
        
        Map<UUID, BigDecimal> valores = Map.of(varId, new BigDecimal("42"));
        assertEquals(new BigDecimal("42"), ast.evaluate(valores));
    }
    
    @Test
    void parsear_formulaConVariables_funciona() {
        UUID varA = UUID.randomUUID();
        UUID varB = UUID.randomUUID();
        String expresion = "${" + varA + "} / ${" + varB + "} * 100";
        
        List<Token> tokens = new FormulaTokenizer(expresion).tokenize();
        ASTNode ast = new FormulaParser(tokens).parse();
        
        Map<UUID, BigDecimal> valores = Map.of(
            varA, new BigDecimal("42"),
            varB, new BigDecimal("50")
        );
        
        // 42 / 50 * 100 = 84
        assertEquals(new BigDecimal("84.0000"), ast.evaluate(valores));
    }
    
    @Test
    void parsear_unarioNegativo_funciona() {
        List<Token> tokens = new FormulaTokenizer("-5 + 2").tokenize();
        ASTNode ast = new FormulaParser(tokens).parse();
        
        assertEquals(new BigDecimal("-3"), ast.evaluate(Map.of()));
    }
    
    @Test
    void parsear_expresionVacia_lanzaExcepcion() {
        List<Token> tokens = new FormulaTokenizer("").tokenize();
        
        assertThrows(IllegalArgumentException.class, () -> {
            new FormulaParser(tokens).parse();
        });
    }
    
    @Test
    void parsear_expresionInvalida_lanzaExcepcion() {
        List<Token> tokens = new FormulaTokenizer("2 +").tokenize();
        
        assertThrows(IllegalArgumentException.class, () -> {
            new FormulaParser(tokens).parse();
        });
    }
    
    @Test
    void parsear_parentesisSinCerrar_lanzaExcepcion() {
        List<Token> tokens = new FormulaTokenizer("(2 + 3").tokenize();
        
        assertThrows(IllegalArgumentException.class, () -> {
            new FormulaParser(tokens).parse();
        });
    }
}
