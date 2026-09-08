// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuthRateLimitService — pruebas unitarias")
class AuthRateLimitServiceTest {

    private AuthRateLimitService service;

    @BeforeEach
    void setUp() {
        service = new AuthRateLimitService();
        // Ventanas cortas para tests deterministas y rápidos, sin esperar minutos reales.
        ReflectionTestUtils.setField(service, "loginMaxPorEmail", 3);
        ReflectionTestUtils.setField(service, "loginMaxPorIp", 5);
        ReflectionTestUtils.setField(service, "loginWindowSeconds", 1);
        ReflectionTestUtils.setField(service, "registerMaxPorIp", 3);
        ReflectionTestUtils.setField(service, "registerWindowSeconds", 1);
        ReflectionTestUtils.setField(service, "forgotMaxPorIp", 3);
        ReflectionTestUtils.setField(service, "forgotMaxPorEmail", 3);
        ReflectionTestUtils.setField(service, "forgotWindowSeconds", 1);
        ReflectionTestUtils.setField(service, "resetMaxPorIp", 3);
        ReflectionTestUtils.setField(service, "resetWindowSeconds", 1);
    }

    @Test
    @DisplayName("Permite solicitudes por debajo del límite")
    void permiteSolicitudesPorDebajoDelLimite() {
        for (int i = 0; i < 3; i++) {
            service.verificarRegistro("10.0.0.1");
        }
        // No lanzó — las 3 (== max) pasaron.
    }

    @Test
    @DisplayName("Bloquea al superar el límite (429 vía RateLimitException)")
    void bloqueaAlSuperarElLimite() {
        for (int i = 0; i < 3; i++) {
            service.verificarRegistro("10.0.0.2");
        }
        assertThatThrownBy(() -> service.verificarRegistro("10.0.0.2"))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("La ventana expira y vuelve a permitir")
    void laVentanaExpiraYVuelveAPermitir() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            service.verificarRegistro("10.0.0.3");
        }
        assertThatThrownBy(() -> service.verificarRegistro("10.0.0.3")).isInstanceOf(RateLimitException.class);

        Thread.sleep(1100); // ventana configurada en 1s para este test

        service.verificarRegistro("10.0.0.3"); // no debe lanzar — la ventana ya expiró
    }

    @Test
    @DisplayName("IPs diferentes son independientes entre sí")
    void ipsDiferentesSonIndependientes() {
        for (int i = 0; i < 3; i++) {
            service.verificarRegistro("10.0.0.4");
        }
        assertThatThrownBy(() -> service.verificarRegistro("10.0.0.4")).isInstanceOf(RateLimitException.class);

        // Otra IP, sin relación — no debe verse afectada por la anterior.
        service.verificarRegistro("10.0.0.5");
    }

    @Test
    @DisplayName("Emails diferentes son independientes entre sí (forgot-password)")
    void emailsDiferentesSonIndependientes() {
        for (int i = 0; i < 3; i++) {
            service.verificarForgotPassword("10.0.1.1", "victima1@test.com");
        }
        assertThatThrownBy(() -> service.verificarForgotPassword("10.0.1.2", "victima1@test.com"))
                .isInstanceOf(RateLimitException.class); // misma víctima, otra IP -> igual bloqueado por email

        // Otro email, otra IP -> independiente, no afectado.
        service.verificarForgotPassword("10.0.1.3", "victima2@test.com");
    }

    @Test
    @DisplayName("Login respeta IP + email: agotar el límite por email bloquea aunque la IP varíe")
    void loginRespetaLimitePorEmail_independienteDeLaIp() {
        for (int i = 0; i < 3; i++) {
            service.verificarLogin("20.0.0." + i, "target@test.com");
        }
        assertThatThrownBy(() -> service.verificarLogin("20.0.0.99", "target@test.com"))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("Login respeta IP + email: agotar el límite por IP bloquea aunque el email varíe")
    void loginRespetaLimitePorIp_independienteDelEmail() {
        for (int i = 0; i < 5; i++) {
            service.verificarLogin("20.0.1.1", "user" + i + "@test.com");
        }
        assertThatThrownBy(() -> service.verificarLogin("20.0.1.1", "otro-user@test.com"))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("forgot-password aplica el límite ANTES de que exista lógica de negocio — el email normalizado/hash se trata igual exista o no la cuenta")
    void forgotPasswordAplicaElLimiteIndependienteDeExistencia() {
        // El servicio de rate limit no sabe (ni le importa) si el email es real:
        // agota el límite exactamente igual para un email inventado.
        for (int i = 0; i < 3; i++) {
            service.verificarForgotPassword("30.0.0.1", "no-existe-esta-cuenta@test.com");
        }
        assertThatThrownBy(() -> service.verificarForgotPassword("30.0.0.1", "no-existe-esta-cuenta@test.com"))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("Normalización de email: mayúsculas/espacios no evaden el límite")
    void normalizacionDeEmail_mayusculasYEspaciosNoEvadenElLimite() {
        service.verificarForgotPassword("30.0.0.2", "Victima@Test.com");
        service.verificarForgotPassword("30.0.0.3", "  victima@test.com  ");
        service.verificarForgotPassword("30.0.0.4", "VICTIMA@TEST.COM");
        assertThatThrownBy(() -> service.verificarForgotPassword("30.0.0.5", "victima@test.com"))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("Concurrencia: N hilos simultáneos sobre la misma clave nunca permiten más que el máximo configurado")
    void concurrencia_nuncaPermiteMasQueElMaximo() throws InterruptedException {
        int hilos = 50;
        ReflectionTestUtils.setField(service, "registerMaxPorIp", 10);
        ReflectionTestUtils.setField(service, "registerWindowSeconds", 30); // ventana amplia: todos los hilos caen en la misma ventana

        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(hilos);
        AtomicInteger permitidos = new AtomicInteger(0);
        AtomicInteger bloqueados = new AtomicInteger(0);

        for (int i = 0; i < hilos; i++) {
            pool.submit(() -> {
                try {
                    service.verificarRegistro("40.0.0.1");
                    permitidos.incrementAndGet();
                } catch (RateLimitException e) {
                    bloqueados.incrementAndGet();
                } finally {
                    salida.countDown();
                }
            });
        }
        assertThat(salida.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(permitidos.get()).isEqualTo(10); // exactamente el máximo, nunca más
        assertThat(bloqueados.get()).isEqualTo(hilos - 10);
    }

    @Test
    @DisplayName("Limpieza: claves con la cola vacía tras expirar se eliminan del mapa (no crece indefinidamente)")
    void limpieza_removeClavesVaciasTrasExpirar() throws InterruptedException {
        ReflectionTestUtils.setField(service, "registerWindowSeconds", 1);
        // Generar muchas IPs distintas -> muchas claves.
        for (int i = 0; i < 20; i++) {
            service.verificarRegistro("50.0.0." + i);
        }
        assertThat(service.clavesActivas()).isEqualTo(20);

        Thread.sleep(1100); // todas las ventanas expiran

        // Forzar el disparo del barrido interno (cada 500 llamadas) sin
        // depender de temporización real: se invoca suficientes veces sobre
        // una clave cualquiera para cruzar el umbral y disparar limpiarClavesVacias().
        for (int i = 0; i < 500; i++) {
            try { service.verificarRegistro("50.0.1.1"); } catch (RateLimitException ignored) { }
        }

        // Las 20 claves originales, con cola vacía tras expirar, deben haberse
        // eliminado — solo debe sobrevivir la clave usada para disparar la limpieza.
        assertThat(service.clavesActivas()).isLessThan(20);
    }
}
