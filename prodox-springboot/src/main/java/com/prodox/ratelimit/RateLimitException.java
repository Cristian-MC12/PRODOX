// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.ratelimit;

/**
 * Excepción lanzada cuando un usuario excede el límite de rate limiting.
 * Será capturada por GlobalExceptionHandler y retornará HTTP 429.
 */
public class RateLimitException extends RuntimeException {
    
    public RateLimitException(String message) {
        super(message);
    }
}
