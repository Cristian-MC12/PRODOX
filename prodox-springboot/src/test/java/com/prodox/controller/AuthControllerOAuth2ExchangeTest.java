// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.security.JwtUtil;
import com.prodox.security.OAuth2ExchangeCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bloque de seguridad JWT/OAuth2 — POST /api/auth/oauth2/exchange, contra
 * el AuthController real, el OAuth2ExchangeCodeService real (sin mocks) y
 * el SecurityFilterChain real (confirma que el endpoint es público, tal
 * como exige la arquitectura: el frontend todavía no tiene JWT en este paso).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerOAuth2ExchangeTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OAuth2ExchangeCodeService exchangeCodeService;
    @Autowired private JwtUtil jwtUtil;

    @BeforeEach
    void limpiarEstado() {
        exchangeCodeService.resetAll();
    }

    private String jwtDePrueba() {
        return jwtUtil.generateToken(UUID.randomUUID().toString(), "exchange-test@prodox.com", "scrum_member", "Exchange Test");
    }

    private String cuerpo(String code) throws Exception {
        return objectMapper.writeValueAsString(Map.of("code", code));
    }

    @Test
    @DisplayName("Código válido: devuelve 200 con el JWT en el cuerpo JSON (nunca en la URL/Location)")
    void codigoValido_devuelveJwtEnElCuerpo() throws Exception {
        String jwt = jwtDePrueba();
        String code = exchangeCodeService.emitirCodigo(jwt);

        mockMvc.perform(post("/api/auth/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(code)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.token").value(jwt))
                .andExpect(jsonPath("$.email").value("exchange-test@prodox.com"))
                .andExpect(jsonPath("$.role").value("scrum_member"));
    }

    @Test
    @DisplayName("Código ya usado: el segundo intento con el MISMO código es rechazado con 400 genérico")
    void codigoYaUsado_rechazado() throws Exception {
        String code = exchangeCodeService.emitirCodigo(jwtDePrueba());

        mockMvc.perform(post("/api/auth/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(code)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Código de intercambio inválido o expirado."));
    }

    @Test
    @DisplayName("Código inexistente: rechazado con 400 genérico, sin revelar detalle interno")
    void codigoInexistente_rechazado() throws Exception {
        mockMvc.perform(post("/api/auth/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("codigo-que-nunca-existio-" + UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Código de intercambio inválido o expirado."));
    }

    @Test
    @DisplayName("Código manipulado: rechazado con el mismo error genérico que un código inexistente (no distingue el caso)")
    void codigoManipulado_rechazado() throws Exception {
        String code = exchangeCodeService.emitirCodigo(jwtDePrueba());
        String manipulado = code.substring(0, code.length() - 1) + (code.endsWith("A") ? "B" : "A");

        mockMvc.perform(post("/api/auth/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(manipulado)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Expiración: código fuera de la ventana configurada es rechazado")
    void codigoExpirado_rechazado() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(exchangeCodeService, "ttlSeconds", 1L);
        try {
            String code = exchangeCodeService.emitirCodigo(jwtDePrueba());
            Thread.sleep(1100);

            mockMvc.perform(post("/api/auth/oauth2/exchange")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(code)))
                    .andExpect(status().isBadRequest());
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(exchangeCodeService, "ttlSeconds", 60L);
        }
    }

    @Test
    @DisplayName("Body vacío/sin campo code: rechazado por validación (400), nunca 200")
    void sinCampoCode_rechazadoPorValidacion() throws Exception {
        mockMvc.perform(post("/api/auth/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("El endpoint es público — no requiere Authorization (el frontend todavía no tiene JWT en este paso)")
    void endpointEsPublico_sinAuthorizationHeader() throws Exception {
        String code = exchangeCodeService.emitirCodigo(jwtDePrueba());
        // Ninguna cabecera Authorization en este request — debe funcionar igual.
        mockMvc.perform(post("/api/auth/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(code)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Concurrencia: dos peticiones simultáneas con el MISMO código — exactamente una obtiene 200, la otra 400")
    void concurrencia_soloUnaPeticionObtieneElJwt() throws Exception {
        String code = exchangeCodeService.emitirCodigo(jwtDePrueba());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(2);
        AtomicInteger exitosas = new AtomicInteger(0);
        AtomicInteger rechazadas = new AtomicInteger(0);

        Runnable intento = () -> {
            try {
                var result = mockMvc.perform(post("/api/auth/oauth2/exchange")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo(code)))
                        .andReturn();
                int status = result.getResponse().getStatus();
                if (status == 200) exitosas.incrementAndGet();
                else rechazadas.incrementAndGet();
            } catch (Exception e) {
                rechazadas.incrementAndGet();
            } finally {
                salida.countDown();
            }
        };

        pool.submit(intento);
        pool.submit(intento);
        org.junit.jupiter.api.Assertions.assertTrue(salida.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        org.assertj.core.api.Assertions.assertThat(exitosas.get()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(rechazadas.get()).isEqualTo(1);
    }
}
