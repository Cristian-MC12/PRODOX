// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.CalcularMetricaRequest;
import com.mpdia.service.CalculoMetricaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Auditoría transversal: antes de este fix, si calcularMetrica() lanzaba
 * SecurityException (por no ser miembro del proyecto), el controller la capturaba
 * en el catch(Exception) genérico y respondía 500 en vez de 403 — el llamante no
 * podía distinguir "sin permisos" de un error real del servidor.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CalculoMetricaController — mapeo de excepciones")
class CalculoMetricaControllerTest {

    @Mock private CalculoMetricaService calculoService;

    private CalculoMetricaController controller;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        controller = new CalculoMetricaController(calculoService);
        auth = new UsernamePasswordAuthenticationToken("user-externo", null, List.of());
    }

    @Test
    @DisplayName("SecurityException del servicio se mapea a 403, no a 500")
    void calcularMetrica_securityException_retorna403() {
        UUID metricaId = UUID.randomUUID();
        CalcularMetricaRequest request = new CalcularMetricaRequest(UUID.randomUUID(), UUID.randomUUID());

        when(calculoService.calcularMetrica(any(), any(), any()))
                .thenThrow(new SecurityException("No tienes acceso a este proyecto"));

        ResponseEntity<?> respuesta = controller.calcularMetrica(metricaId, request, auth);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("IllegalArgumentException del servicio sigue mapeándose a 400")
    void calcularMetrica_illegalArgumentException_retorna400() {
        UUID metricaId = UUID.randomUUID();
        CalcularMetricaRequest request = new CalcularMetricaRequest(UUID.randomUUID(), UUID.randomUUID());

        when(calculoService.calcularMetrica(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Datos inválidos"));

        ResponseEntity<?> respuesta = controller.calcularMetrica(metricaId, request, auth);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
