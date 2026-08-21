// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.formula;

/**
 * Token para el parser de fórmulas matemáticas.
 * Fase 16.8: Motor determinista de cálculo.
 */
public record Token(TokenType type, String value) {
    
    public enum TokenType {
        NUMBER,      // 42, 3.14
        VARIABLE,    // ${uuid}
        PLUS,        // +
        MINUS,       // -
        MULTIPLY,    // *
        DIVIDE,      // /
        LPAREN,      // (
        RPAREN,      // )
        EOF          // Fin de expresión
    }
    
    public static Token number(String value) {
        return new Token(TokenType.NUMBER, value);
    }
    
    public static Token variable(String value) {
        return new Token(TokenType.VARIABLE, value);
    }
    
    public static Token operator(TokenType type) {
        return new Token(type, null);
    }
    
    public static Token eof() {
        return new Token(TokenType.EOF, null);
    }
}
