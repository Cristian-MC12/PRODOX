// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Servicio de Rate Limiting para proteger el AI Copilot contra abuso.
 * 
 * Implementación:
 * - Usa ConcurrentHashMap para thread-safety
 * - Usa ventana deslizante (sliding window) de 1 minuto
 * - Límite configurable por usuario autenticado
 * - Identificación por userId del JWT (no IP, no frontend)
 * 
 * IMPORTANTE:
 * - Esta implementación es para instancia única (desarrollo/producción pequeña)
 * - Para producción con múltiples instancias, migrar a Redis o similar
 */
@Slf4j
@Service
public class RateLimitService {

    @Value("${prodox.ai.rate-limit.requests-per-minute:10}")
    private int requestsPerMinute;

    @Value("${prodox.ai.rate-limit.window-seconds:60}")
    private int windowSeconds;

    // Map: userId -> Queue de timestamps
    private final ConcurrentHashMap<String, Queue<Instant>> requestHistory = new ConcurrentHashMap<>();

    /**
     * Verifica si el usuario puede hacer un request.
     * 
     * @param userId ID del usuario autenticado (desde JWT)
     * @return true si está dentro del límite, false si excede
     */
    public boolean allowRequest(String userId) {
        if (userId == null || userId.isBlank()) {
            log.warn("RateLimit: userId nulo o vacío");
            return false;
        }

        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowSeconds);

        // Obtener o crear queue para este usuario
        Queue<Instant> userRequests = requestHistory.computeIfAbsent(
            userId, 
            k -> new ConcurrentLinkedQueue<>()
        );

        // Limpiar requests fuera de la ventana
        synchronized (userRequests) {
            userRequests.removeIf(timestamp -> timestamp.isBefore(windowStart));

            // Verificar límite
            if (userRequests.size() >= requestsPerMinute) {
                log.warn("RateLimit: Usuario {} excedió el límite de {} requests/minuto", 
                         userId, requestsPerMinute);
                return false;
            }

            // Registrar request
            userRequests.add(now);
            log.debug("RateLimit: Usuario {} tiene {} requests en ventana de {}s", 
                     userId, userRequests.size(), windowSeconds);
            return true;
        }
    }

    /**
     * Obtiene el número actual de requests del usuario en la ventana.
     * Útil para debugging y testing.
     */
    public int getCurrentRequestCount(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }

        Queue<Instant> userRequests = requestHistory.get(userId);
        if (userRequests == null) {
            return 0;
        }

        Instant windowStart = Instant.now().minusSeconds(windowSeconds);
        
        synchronized (userRequests) {
            // Limpiar requests antiguos
            userRequests.removeIf(timestamp -> timestamp.isBefore(windowStart));
            return userRequests.size();
        }
    }

    /**
     * Limpia el historial de un usuario (útil para testing).
     */
    public void resetUser(String userId) {
        requestHistory.remove(userId);
        log.debug("RateLimit: Historial de usuario {} limpiado", userId);
    }

    /**
     * Limpia TODO el historial (útil para testing).
     */
    public void resetAll() {
        requestHistory.clear();
        log.debug("RateLimit: Historial completo limpiado");
    }

    /**
     * Obtiene el límite configurado.
     */
    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    /**
     * Obtiene la ventana en segundos.
     */
    public int getWindowSeconds() {
        return windowSeconds;
    }
}
