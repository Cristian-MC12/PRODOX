// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.ratelimit;

/**
 * Excepción lanzada cuando un usuario excede el límite de rate limiting.
 * Será capturada por GlobalExceptionHandler y retornará HTTP 429.
 */
public class RateLimitException extends RuntimeException {
    
    public RateLimitException(String message) {
        super(message);
    }
}
