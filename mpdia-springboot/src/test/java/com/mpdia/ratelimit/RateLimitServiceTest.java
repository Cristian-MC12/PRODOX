package com.mpdia.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del RateLimitService.
 * 
 * Valida:
 * - Límite por usuario
 * - Ventana deslizante
 * - Independencia entre usuarios
 * - Thread safety básico
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "mpdia.ai.rate-limit.requests-per-minute=3",
    "mpdia.ai.rate-limit.window-seconds=60"
})
class RateLimitServiceTest {

    @Autowired
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService.resetAll();
    }

    @Test
    void allowRequest_primerRequest_permitido() {
        // Given
        String userId = "user123";

        // When
        boolean allowed = rateLimitService.allowRequest(userId);

        // Then
        assertThat(allowed).isTrue();
        assertThat(rateLimitService.getCurrentRequestCount(userId)).isEqualTo(1);
    }

    @Test
    void allowRequest_dentroDeLimite_permitido() {
        // Given
        String userId = "user123";
        
        // When - hacer 3 requests (límite es 3)
        boolean r1 = rateLimitService.allowRequest(userId);
        boolean r2 = rateLimitService.allowRequest(userId);
        boolean r3 = rateLimitService.allowRequest(userId);

        // Then
        assertThat(r1).isTrue();
        assertThat(r2).isTrue();
        assertThat(r3).isTrue();
        assertThat(rateLimitService.getCurrentRequestCount(userId)).isEqualTo(3);
    }

    @Test
    void allowRequest_excedeElLimite_rechazado() {
        // Given
        String userId = "user123";
        
        // When - hacer 4 requests (límite es 3)
        rateLimitService.allowRequest(userId);
        rateLimitService.allowRequest(userId);
        rateLimitService.allowRequest(userId);
        boolean r4 = rateLimitService.allowRequest(userId);

        // Then
        assertThat(r4).isFalse();
        assertThat(rateLimitService.getCurrentRequestCount(userId)).isEqualTo(3);
    }

    @Test
    void allowRequest_usuariosDiferentes_independientes() {
        // Given
        String user1 = "user123";
        String user2 = "user456";
        
        // When - user1 llega al límite
        rateLimitService.allowRequest(user1);
        rateLimitService.allowRequest(user1);
        rateLimitService.allowRequest(user1);
        boolean user1Rechazado = rateLimitService.allowRequest(user1);

        // Then - user2 aún puede hacer requests
        boolean user2Permitido = rateLimitService.allowRequest(user2);
        assertThat(user1Rechazado).isFalse();
        assertThat(user2Permitido).isTrue();
        assertThat(rateLimitService.getCurrentRequestCount(user1)).isEqualTo(3);
        assertThat(rateLimitService.getCurrentRequestCount(user2)).isEqualTo(1);
    }

    @Test
    void allowRequest_userIdNulo_rechazado() {
        // When
        boolean allowed = rateLimitService.allowRequest(null);

        // Then
        assertThat(allowed).isFalse();
    }

    @Test
    void allowRequest_userIdVacio_rechazado() {
        // When
        boolean allowed = rateLimitService.allowRequest("");

        // Then
        assertThat(allowed).isFalse();
    }

    @Test
    void resetUser_limpiaHistorial() {
        // Given
        String userId = "user123";
        rateLimitService.allowRequest(userId);
        rateLimitService.allowRequest(userId);

        // When
        rateLimitService.resetUser(userId);

        // Then
        assertThat(rateLimitService.getCurrentRequestCount(userId)).isEqualTo(0);
        
        // Y puede hacer requests nuevamente
        boolean allowed = rateLimitService.allowRequest(userId);
        assertThat(allowed).isTrue();
    }

    @Test
    void getCurrentRequestCount_usuarioSinRequests_retornaCero() {
        // When
        int count = rateLimitService.getCurrentRequestCount("user999");

        // Then
        assertThat(count).isEqualTo(0);
    }

    @Test
    void getConfiguration_retornaValoresConfigurados() {
        // When
        int requestsPerMinute = rateLimitService.getRequestsPerMinute();
        int windowSeconds = rateLimitService.getWindowSeconds();

        // Then - valores de @TestPropertySource
        assertThat(requestsPerMinute).isEqualTo(3);
        assertThat(windowSeconds).isEqualTo(60);
    }

    @Test
    void allowRequest_concurrencia_threadSafe() throws InterruptedException {
        // Given
        String userId = "user123";
        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];

        // When - 5 threads hacen requests simultáneos
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> rateLimitService.allowRequest(userId));
            threads[i].start();
        }

        // Esperar a que terminen todos los threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - el contador final debe ser correcto (máximo 3, que es el límite)
        int finalCount = rateLimitService.getCurrentRequestCount(userId);
        assertThat(finalCount).isLessThanOrEqualTo(3);
    }
}
