// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bloque de seguridad Validación/Config (Sub-bloque 3B, C3) — tests
 * unitarios directos sobre RequestSizeLimitFilter, sin levantar el contexto
 * de Spring: verifican con precisión el límite exacto (1 MB), el corte
 * durante la lectura del body cuando Content-Length no está disponible, y
 * que la respuesta 413 no expone información interna.
 *
 * Ver AuthControllerPasswordLimitTest y el resto de la suite de
 * controllers (todos corren con este filtro activo en el contexto real,
 * @SpringBootTest) para la prueba de que endpoints legítimos por debajo del
 * límite siguen funcionando sin cambios.
 */
class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter();

    private static ByteArrayOutputStream capturarRespuesta(HttpServletResponse response) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(salida, true, StandardCharsets.UTF_8);
        when(response.getWriter()).thenReturn(writer);
        return salida;
    }

    @Test
    @DisplayName("Content-Length debajo del límite: continúa normalmente, sin 413")
    void contentLengthDebajoDelLimite_continuaNormalmente() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getContentLengthLong()).thenReturn(1000L);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(any(), eq(response));
        verify(response, never()).setStatus(413);
    }

    @Test
    @DisplayName("Content-Length exactamente en el límite (1.048.576 bytes): permitido")
    void contentLengthExactamenteEnElLimite_permitido() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getContentLengthLong()).thenReturn(RequestSizeLimitFilter.MAX_BODY_BYTES);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(any(), eq(response));
        verify(response, never()).setStatus(413);
    }

    @Test
    @DisplayName("Content-Length supera el límite: rechazado con 413, sin llegar al resto de la cadena")
    void contentLengthSuperaElLimite_rechazado413() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getContentLengthLong()).thenReturn(RequestSizeLimitFilter.MAX_BODY_BYTES + 1);
        ByteArrayOutputStream salida = capturarRespuesta(response);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        verify(response).setStatus(413);
        verify(response).setContentType("application/json");

        String cuerpo = salida.toString(StandardCharsets.UTF_8);
        assertThat(cuerpo).contains("\"status\":413");
        assertThat(cuerpo).doesNotContain("Exception");
        assertThat(cuerpo).doesNotContain("com.prodox");
        assertThat(cuerpo).doesNotContain("java.lang");
        assertThat(cuerpo).doesNotContain("\tat ");
    }

    @Test
    @DisplayName("GET/sin body (Content-Length ausente, sin lectura de stream): no se ve afectado")
    void getSinBody_noAfectado() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class); // nunca lee el body — comportamiento típico de un GET
        when(request.getContentLengthLong()).thenReturn(-1L); // Content-Length ausente

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(any(), eq(response));
        verify(response, never()).setStatus(413);
    }

    @Test
    @DisplayName("Sin Content-Length confiable pero el body real supera 1 MB al leerlo: rechazado con 413")
    void sinContentLengthPeroBodyRealSuperaElLimite_rechazado413() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getContentLengthLong()).thenReturn(-1L); // como en un POST chunked

        byte[] bodyDeMasDeUnMB = new byte[(int) RequestSizeLimitFilter.MAX_BODY_BYTES + 1000];
        when(request.getInputStream()).thenReturn(new FakeServletInputStream(bodyDeMasDeUnMB));

        // Simula lo que Jackson/Spring MVC hacen: leer el body del request YA
        // ENVUELTO que el filtro les pasa, byte a byte hasta agotarlo o hasta
        // que el envoltorio corte con RequestBodyTooLargeException.
        FilterChain chain = (req, res) -> {
            ServletInputStream in = ((HttpServletRequest) req).getInputStream();
            byte[] buffer = new byte[8192];
            while (in.read(buffer) != -1) {
                // sigue leyendo — se espera que el wrapper corte antes de agotar el body
            }
        };
        ByteArrayOutputStream salida = capturarRespuesta(response);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(413);
        String cuerpo = salida.toString(StandardCharsets.UTF_8);
        assertThat(cuerpo).doesNotContain("Exception");
        assertThat(cuerpo).doesNotContain("com.prodox");
    }

    @Test
    @DisplayName("Sin Content-Length y el body real está por debajo de 1 MB: se lee completo sin cortar")
    void sinContentLengthYBodyDebajoDelLimite_seLeeCompleto() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getContentLengthLong()).thenReturn(-1L);

        byte[] bodyPequeno = "{\"email\":\"a@b.com\"}".getBytes(StandardCharsets.UTF_8);
        when(request.getInputStream()).thenReturn(new FakeServletInputStream(bodyPequeno));

        ByteArrayOutputStream leido = new ByteArrayOutputStream();
        FilterChain chain = (req, res) -> {
            ServletInputStream in = ((HttpServletRequest) req).getInputStream();
            int b;
            while ((b = in.read()) != -1) {
                leido.write(b);
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(leido.toString(StandardCharsets.UTF_8)).isEqualTo("{\"email\":\"a@b.com\"}");
        verify(response, never()).setStatus(413);
    }

    /** ServletInputStream mínimo respaldado por un array de bytes, para simular el body real de un request. */
    private static final class FakeServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        FakeServletInputStream(byte[] data) {
            this.delegate = new ByteArrayInputStream(data);
        }

        @Override public int read() { return delegate.read(); }
        @Override public int read(byte[] b, int off, int len) { return delegate.read(b, off, len); }
        @Override public boolean isFinished() { return delegate.available() == 0; }
        @Override public boolean isReady() { return true; }
        @Override public void setReadListener(ReadListener readListener) { /* no-op para este test */ }
    }
}
