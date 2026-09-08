// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.prodox.dto.ai.gemini.Message;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bloque de seguridad Secretos/Config (Sub-bloque 4A) — GeminiService
 * construye la URL de cada llamada como "apiUrl + \"?key=\" + apiKey". Antes
 * de esta corrección, un fallo de I/O real (timeout, DNS, conexión
 * rechazada) hacía que Spring lanzara ResourceAccessException con esa URI
 * completa (API key incluida) en su mensaje, y GeminiService reimprimía ese
 * mensaje tal cual con System.err.println/log.error y lo reempaquetaba
 * dentro de una nueva RuntimeException — propagando la API key real hasta
 * los logs y hasta cualquier código que leyera esa excepción.
 *
 * Estos tests usan un servidor HTTP local real (com.sun.net.httpserver,
 * incluido en el JDK, sin dependencias nuevas) para provocar tanto el
 * camino exitoso como un fallo de I/O genuino (conexión rechazada contra un
 * puerto cerrado), y verifican con Logback ListAppender que la API key de
 * prueba NUNCA aparece ni en la excepción resultante ni en ningún log.
 */
class GeminiServiceSecurityTest {

    private static final String SECRET_TEST_VALUE = "SECRET_TEST_VALUE_XYZ123";

    private GeminiService geminiService;
    private HttpServer server;
    private ListAppender<ILoggingEvent> logAppender;
    private ch.qos.logback.classic.Logger logbackLogger;

    @BeforeEach
    void setUp() {
        geminiService = new GeminiService();
        ReflectionTestUtils.setField(geminiService, "apiKey", SECRET_TEST_VALUE);

        logbackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(GeminiService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logbackLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logbackLogger.detachAppender(logAppender);
        if (server != null) {
            server.stop(0);
        }
    }

    private String iniciarServidorConRespuestaGemini(String textoRespuesta) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1beta/models/gemini", exchange -> {
            String json = """
                {"candidates":[{"content":{"parts":[{"text":"%s"}]}}]}
                """.formatted(textoRespuesta);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/v1beta/models/gemini";
    }

    private String iniciarServidorConErrorHttp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1beta/models/gemini", exchange -> {
            String json = "{\"error\":{\"message\":\"API key not valid\"}}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/v1beta/models/gemini";
    }

    // ─────────────────────────────────────────────────────────────────
    // 7) Camino exitoso: generate() y chatWithTools() siguen funcionando
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("generate(): camino exitoso sigue devolviendo el texto de Gemini sin cambios")
    void generate_caminoExitoso_siguenFuncionando() throws IOException {
        String url = iniciarServidorConRespuestaGemini("Hola desde Gemini");
        ReflectionTestUtils.setField(geminiService, "apiUrl", url);

        String resultado = geminiService.generate("un prompt cualquiera");

        assertThat(resultado).isEqualTo("Hola desde Gemini");
    }

    @Test
    @DisplayName("chatWithTools(): camino exitoso sigue devolviendo el texto de Gemini sin cambios")
    void chatWithTools_caminoExitoso_siguePasandoAlServicio() throws IOException {
        String url = iniciarServidorConRespuestaGemini("Respuesta con tools");
        ReflectionTestUtils.setField(geminiService, "apiUrl", url);

        var respuesta = geminiService.chatWithTools(List.of(Message.user("hola")), null, null);

        assertThat(respuesta.text()).isEqualTo("Respuesta con tools");
    }

    // ─────────────────────────────────────────────────────────────────
    // 1)/2) Fallo de I/O real: ni la excepción ni los logs contienen la key
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("generate(): fallo de I/O real — la excepción resultante NO contiene la API key")
    void generate_falloDeIO_excepcionNoContieneLaApiKey() {
        // Puerto cerrado real: conexión rechazada de inmediato (sin esperar timeout),
        // produce un ResourceAccessException real de Spring cuyo mensaje estándar
        // incluye la URI completa de la petición — la misma que construye
        // GeminiService con "?key=" + apiKey.
        ReflectionTestUtils.setField(geminiService, "apiUrl", "http://127.0.0.1:1/v1beta/models/gemini");

        assertThatThrownBy(() -> geminiService.generate("prompt"))
                .isInstanceOf(RuntimeException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(SECRET_TEST_VALUE);
                    // Tampoco debe encadenarse la excepción original como causa.
                    assertThat(e.getCause()).isNull();
                });
    }

    @Test
    @DisplayName("generate(): fallo de I/O real — ningún log contiene la API key")
    void generate_falloDeIO_logsNoContienenLaApiKey() {
        ReflectionTestUtils.setField(geminiService, "apiUrl", "http://127.0.0.1:1/v1beta/models/gemini");

        assertThatThrownBy(() -> geminiService.generate("prompt")).isInstanceOf(RuntimeException.class);

        List<String> mensajesLogueados = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertThat(mensajesLogueados).isNotEmpty(); // sí se registró *algo* (tipo de error)
        assertThat(mensajesLogueados).noneMatch(m -> m.contains(SECRET_TEST_VALUE));

        // También el mensaje de cada evento logueado, incluyendo cualquier throwable adjunto.
        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(SECRET_TEST_VALUE);
            if (event.getThrowableProxy() != null) {
                assertThat(event.getThrowableProxy().getMessage()).doesNotContain(SECRET_TEST_VALUE);
            }
        }
    }

    @Test
    @DisplayName("chatWithTools(): fallo de I/O real — ni la excepción ni los logs contienen la API key")
    void chatWithTools_falloDeIO_niExcepcionNiLogsContienenLaApiKey() {
        ReflectionTestUtils.setField(geminiService, "apiUrl", "http://127.0.0.1:1/v1beta/models/gemini");

        assertThatThrownBy(() -> geminiService.chatWithTools(List.of(Message.user("hola")), null, null))
                .isInstanceOf(RuntimeException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(SECRET_TEST_VALUE));

        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(SECRET_TEST_VALUE);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 8) Error HTTP (no I/O) sigue manejándose igual que antes (sin cambios
    //    de comportamiento en ese camino, que nunca llevaba la key)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("generate(): error HTTP de Gemini (ej. 400) sigue propagando el cuerpo del error, sin la API key (nunca la tuvo)")
    void generate_errorHttp_siguePropagandoElCuerpoDelError() throws IOException {
        String url = iniciarServidorConErrorHttp();
        ReflectionTestUtils.setField(geminiService, "apiUrl", url);

        assertThatThrownBy(() -> geminiService.generate("prompt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("API key not valid")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(SECRET_TEST_VALUE));
    }
}
