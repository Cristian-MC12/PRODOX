// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth2ExchangeCodeService — pruebas unitarias")
class OAuth2ExchangeCodeServiceTest {

    private OAuth2ExchangeCodeService service;

    @BeforeEach
    void setUp() {
        service = new OAuth2ExchangeCodeService();
        ReflectionTestUtils.setField(service, "ttlSeconds", 1L); // ventana corta para tests deterministas
    }

    @Test
    @DisplayName("Código válido devuelve el JWT asociado")
    void codigoValido_devuelveElJwt() {
        String codigo = service.emitirCodigo("jwt-de-prueba");
        assertThat(service.canjear(codigo)).contains("jwt-de-prueba");
    }

    @Test
    @DisplayName("El código solo puede canjearse una vez — el segundo intento con el MISMO código es rechazado")
    void codigoSoloUnaVez() {
        String codigo = service.emitirCodigo("jwt-de-prueba");
        assertThat(service.canjear(codigo)).isPresent();
        assertThat(service.canjear(codigo)).isEmpty();
    }

    @Test
    @DisplayName("Código inexistente es rechazado")
    void codigoInexistente_rechazado() {
        assertThat(service.canjear("codigo-que-nunca-se-emitio")).isEmpty();
    }

    @Test
    @DisplayName("Código manipulado (alterado un carácter de uno real) es rechazado")
    void codigoManipulado_rechazado() {
        String codigo = service.emitirCodigo("jwt-de-prueba");
        String manipulado = codigo.substring(0, codigo.length() - 1) + (codigo.endsWith("A") ? "B" : "A");
        assertThat(service.canjear(manipulado)).isEmpty();
        // El código real, sin manipular, sigue vigente — la manipulación no lo consumió.
        assertThat(service.canjear(codigo)).isPresent();
    }

    @Test
    @DisplayName("Código nulo o vacío es rechazado sin lanzar excepción")
    void codigoNuloOVacio_rechazadoSinExcepcion() {
        assertThat(service.canjear(null)).isEmpty();
        assertThat(service.canjear("")).isEmpty();
        assertThat(service.canjear("   ")).isEmpty();
    }

    @Test
    @DisplayName("Código expirado (fuera de la ventana configurada) es rechazado")
    void codigoExpirado_rechazado() throws InterruptedException {
        String codigo = service.emitirCodigo("jwt-de-prueba");
        Thread.sleep(1100); // ventana de 1s configurada en setUp
        assertThat(service.canjear(codigo)).isEmpty();
    }

    @Test
    @DisplayName("Un código expirado no puede canjearse ni siquiera antes de ser accedido — queda inutilizado permanentemente")
    void codigoExpirado_permaneceInutilizadoAunSinCanjePrevio() throws InterruptedException {
        String codigo = service.emitirCodigo("jwt-de-prueba");
        Thread.sleep(1100);
        assertThat(service.canjear(codigo)).isEmpty();
        assertThat(service.canjear(codigo)).isEmpty(); // sigue vacío, no "revive"
    }

    @Test
    @DisplayName("Códigos generados son distintos entre sí (aleatoriedad real, no un contador predecible)")
    void codigosGeneradosSonDistintos() {
        String c1 = service.emitirCodigo("jwt-1");
        String c2 = service.emitirCodigo("jwt-2");
        String c3 = service.emitirCodigo("jwt-3");
        assertThat(c1).isNotEqualTo(c2).isNotEqualTo(c3);
        assertThat(c2).isNotEqualTo(c3);
        // 256 bits en base64url sin padding -> 43 caracteres, suficientemente largo/no adivinable.
        assertThat(c1.length()).isGreaterThanOrEqualTo(40);
    }

    @Test
    @DisplayName("Concurrencia: N hilos intentando canjear el MISMO código simultáneamente — exactamente uno lo obtiene")
    void concurrencia_soloUnHiloObtieneElJwt() throws InterruptedException {
        String codigo = service.emitirCodigo("jwt-compartido");

        int hilos = 30;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(hilos);
        AtomicInteger exitosos = new AtomicInteger(0);

        for (int i = 0; i < hilos; i++) {
            pool.submit(() -> {
                try {
                    if (service.canjear(codigo).isPresent()) {
                        exitosos.incrementAndGet();
                    }
                } finally {
                    salida.countDown();
                }
            });
        }
        assertThat(salida.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(exitosos.get()).isEqualTo(1); // exactamente uno, nunca cero ni más de uno
    }

    @Test
    @DisplayName("Limpieza: códigos expirados y nunca canjeados se eliminan del mapa (no crecimiento indefinido)")
    void limpieza_removeCodigosExpiradosSinCanjear() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            service.emitirCodigo("jwt-" + i);
        }
        assertThat(service.codigosActivos()).isEqualTo(20);

        Thread.sleep(1100); // todos expiran (ventana de 1s)

        // Disparar el umbral de limpieza (cada 200 llamadas a emitirCodigo()).
        for (int i = 0; i < 200; i++) {
            service.emitirCodigo("jwt-relleno-" + i);
        }

        // Los 20 códigos originales (y buena parte de los de relleno ya
        // expirados en el camino) deben haberse limpiado — el mapa no
        // conserva todo lo emitido indefinidamente.
        assertThat(service.codigosActivos()).isLessThan(220);
    }
}
