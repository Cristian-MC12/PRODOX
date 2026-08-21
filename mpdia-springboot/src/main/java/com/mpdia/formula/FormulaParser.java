// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.formula;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Parser de expresiones matemáticas con precedencia de operadores.
 * Fase 16.8: Convierte tokens en AST respetando precedencia.
 * 
 * Gramática:
 * expression := term ((PLUS | MINUS) term)*
 * term       := factor ((MULTIPLY | DIVIDE) factor)*
 * factor     := (PLUS | MINUS)? primary
 * primary    := NUMBER | VARIABLE | LPAREN expression RPAREN
 */
public class FormulaParser {
    
    private final List<Token> tokens;
    private int current;
    
    public FormulaParser(List<Token> tokens) {
        this.tokens = tokens;
        this.current = 0;
    }
    
    /**
     * Parsea la expresión completa.
     */
    public ASTNode parse() {
        if (tokens.isEmpty() || isAtEnd()) {
            throw new IllegalArgumentException("Expresión vacía");
        }
        
        ASTNode result = expression();
        
        if (!isAtEnd()) {
            throw new IllegalArgumentException(
                "Tokens inesperados después de la expresión: " + currentToken());
        }
        
        return result;
    }
    
    /**
     * expression := term ((PLUS | MINUS) term)*
     */
    private ASTNode expression() {
        ASTNode left = term();
        
        while (match(Token.TokenType.PLUS, Token.TokenType.MINUS)) {
            Token.TokenType opType = previous().type();
            ASTNode.Operator op = opType == Token.TokenType.PLUS 
                ? ASTNode.Operator.ADD 
                : ASTNode.Operator.SUBTRACT;
            ASTNode right = term();
            left = new ASTNode.BinaryOpNode(left, op, right);
        }
        
        return left;
    }
    
    /**
     * term := factor ((MULTIPLY | DIVIDE) factor)*
     */
    private ASTNode term() {
        ASTNode left = factor();
        
        while (match(Token.TokenType.MULTIPLY, Token.TokenType.DIVIDE)) {
            Token.TokenType opType = previous().type();
            ASTNode.Operator op = opType == Token.TokenType.MULTIPLY 
                ? ASTNode.Operator.MULTIPLY 
                : ASTNode.Operator.DIVIDE;
            ASTNode right = factor();
            left = new ASTNode.BinaryOpNode(left, op, right);
        }
        
        return left;
    }
    
    /**
     * factor := (PLUS | MINUS)? primary
     */
    private ASTNode factor() {
        // Operador unario
        if (match(Token.TokenType.MINUS, Token.TokenType.PLUS)) {
            Token.TokenType opType = previous().type();
            ASTNode.Operator op = opType == Token.TokenType.MINUS 
                ? ASTNode.Operator.SUBTRACT 
                : ASTNode.Operator.ADD;
            ASTNode operand = factor();
            return new ASTNode.UnaryOpNode(op, operand);
        }
        
        return primary();
    }
    
    /**
     * primary := NUMBER | VARIABLE | LPAREN expression RPAREN
     */
    private ASTNode primary() {
        // Número
        if (match(Token.TokenType.NUMBER)) {
            String value = previous().value();
            return new ASTNode.NumberNode(new BigDecimal(value));
        }
        
        // Variable
        if (match(Token.TokenType.VARIABLE)) {
            String uuidStr = previous().value();
            UUID variableId = UUID.fromString(uuidStr);
            return new ASTNode.VariableNode(variableId);
        }
        
        // Paréntesis
        if (match(Token.TokenType.LPAREN)) {
            ASTNode expr = expression();
            consume(Token.TokenType.RPAREN, "Se esperaba ')' después de la expresión");
            return expr;
        }
        
        throw new IllegalArgumentException(
            "Token inesperado: " + currentToken() + " en posición " + current);
    }
    
    // ===== Utilidades =====
    
    private boolean match(Token.TokenType... types) {
        for (Token.TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }
    
    private boolean check(Token.TokenType type) {
        if (isAtEnd()) return false;
        return currentToken().type() == type;
    }
    
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }
    
    private void consume(Token.TokenType type, String message) {
        if (check(type)) {
            advance();
            return;
        }
        throw new IllegalArgumentException(message + ". Token actual: " + currentToken());
    }
    
    private boolean isAtEnd() {
        return current >= tokens.size() || currentToken().type() == Token.TokenType.EOF;
    }
    
    private Token currentToken() {
        if (current >= tokens.size()) {
            return Token.eof();
        }
        return tokens.get(current);
    }
    
    private Token previous() {
        return tokens.get(current - 1);
    }
}
