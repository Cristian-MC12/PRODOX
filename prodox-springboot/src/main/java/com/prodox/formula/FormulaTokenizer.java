// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.formula;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tokenizer para expresiones matemáticas.
 * Fase 16.8: Convierte string de fórmula en tokens.
 */
public class FormulaTokenizer {
    
    private final String input;
    private int pos;
    
    public FormulaTokenizer(String input) {
        this.input = input != null ? input.trim() : "";
        this.pos = 0;
    }
    
    /**
     * Tokeniza la expresión completa.
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        
        while (pos < input.length()) {
            char c = currentChar();
            
            // Espacios
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }
            
            // Números
            if (Character.isDigit(c) || c == '.') {
                tokens.add(readNumber());
                continue;
            }
            
            // Variables ${uuid}
            if (c == '$' && peek() == '{') {
                tokens.add(readVariable());
                continue;
            }
            
            // Operadores y paréntesis
            Token op = readOperator();
            if (op != null) {
                tokens.add(op);
                continue;
            }
            
            throw new IllegalArgumentException(
                "Carácter inválido en posición " + pos + ": '" + c + "'");
        }
        
        tokens.add(Token.eof());
        return tokens;
    }
    
    private Token readNumber() {
        StringBuilder sb = new StringBuilder();
        boolean hasDecimal = false;
        
        while (pos < input.length()) {
            char c = currentChar();
            
            if (Character.isDigit(c)) {
                sb.append(c);
                pos++;
            } else if (c == '.' && !hasDecimal) {
                hasDecimal = true;
                sb.append(c);
                pos++;
            } else {
                break;
            }
        }
        
        String numStr = sb.toString();
        if (numStr.isEmpty() || numStr.equals(".")) {
            throw new IllegalArgumentException("Número inválido en posición " + pos);
        }
        
        return Token.number(numStr);
    }
    
    private Token readVariable() {
        // Formato: ${uuid}
        if (currentChar() != '$' || peek() != '{') {
            throw new IllegalArgumentException("Variable debe iniciar con '${' en posición " + pos);
        }
        
        pos += 2; // Saltar ${
        
        StringBuilder sb = new StringBuilder();
        while (pos < input.length() && currentChar() != '}') {
            sb.append(currentChar());
            pos++;
        }
        
        if (pos >= input.length() || currentChar() != '}') {
            throw new IllegalArgumentException("Variable sin cerrar: falta '}'");
        }
        
        pos++; // Saltar }
        
        String uuidStr = sb.toString().trim();
        
        // Validar que sea un UUID válido
        try {
            UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("UUID inválido en variable: " + uuidStr);
        }
        
        return Token.variable(uuidStr);
    }
    
    private Token readOperator() {
        char c = currentChar();
        pos++;
        
        return switch (c) {
            case '+' -> Token.operator(Token.TokenType.PLUS);
            case '-' -> Token.operator(Token.TokenType.MINUS);
            case '*' -> Token.operator(Token.TokenType.MULTIPLY);
            case '/' -> Token.operator(Token.TokenType.DIVIDE);
            case '(' -> Token.operator(Token.TokenType.LPAREN);
            case ')' -> Token.operator(Token.TokenType.RPAREN);
            default -> {
                pos--; // Retroceder si no es operador
                yield null;
            }
        };
    }
    
    private char currentChar() {
        return input.charAt(pos);
    }
    
    private char peek() {
        return pos + 1 < input.length() ? input.charAt(pos + 1) : '\0';
    }
}
