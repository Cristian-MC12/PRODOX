// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bloque de seguridad Validación/Config (Sub-bloque 3B, C3) — límite global
 * de tamaño de request body.
 *
 * Antes de este filtro no existía ningún límite efectivo para un
 * @RequestBody JSON: server.tomcat.max-swallow-size y
 * max-http-form-post-size solo aplican a form-urlencoded/multipart (parseo
 * vía request.getParameter()), nunca a JSON leído por Jackson vía
 * getInputStream(); y los StreamReadConstraints por defecto de Jackson 2.15+
 * solo acotan un String individual (~20 millones de caracteres), no el
 * tamaño TOTAL del body. Ver auditoría del Bloque 3 (C3) para el detalle.
 *
 * Registrado como filtro de servlet plano — @Component + @Order(HIGHEST_PRECEDENCE)
 * — DELIBERADAMENTE fuera de HttpSecurity/SecurityConfig: se ejecuta antes
 * que la cadena de Spring Security (que Spring Boot registra en el orden
 * -100 por defecto), así que un body demasiado grande se rechaza sin gastar
 * ningún trabajo de autenticación/autorización/CORS, y sin tocar el orden de
 * filtros ya definido dentro de SecurityConfig.
 *
 * Cubre dos casos:
 * 1) Content-Length presente y ya supera el límite: se rechaza de inmediato
 *    con 413, sin leer ni un byte del body.
 * 2) Content-Length ausente o no confiable (ej. Transfer-Encoding: chunked):
 *    se envuelve el InputStream para contar los bytes realmente leídos y
 *    cortar en cuanto se supera el límite, mientras el resto de la cadena
 *    (Jackson, vía Spring MVC) sigue leyendo el body con normalidad. Cuando
 *    Jackson topa con el corte, Spring lo envuelve como
 *    HttpMessageNotReadableException — ya manejado por
 *    GlobalExceptionHandler.handleUnreadable() con un 400 genérico sin
 *    stack trace; este filtro además intercepta el caso (poco común) de que
 *    la excepción llegue sin haber pasado por ahí, y devuelve 413 él mismo
 *    si la respuesta todavía no se comprometió.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    /** C3: 1 MB exacto, límite acordado en la auditoría del Bloque 3. */
    static final long MAX_BODY_BYTES = 1_048_576L;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_BODY_BYTES) {
            log.warn("Request rechazado por Content-Length ({} bytes > límite de {} bytes)", contentLength, MAX_BODY_BYTES);
            escribirRespuesta413(response);
            return;
        }

        try {
            filterChain.doFilter(new LimitedBodyRequestWrapper(request, MAX_BODY_BYTES), response);
        } catch (RequestBodyTooLargeException e) {
            log.warn("Request rechazado durante la lectura del body (superó {} bytes)", MAX_BODY_BYTES);
            if (!response.isCommitted()) {
                escribirRespuesta413(response);
            }
        }
    }

    private void escribirRespuesta413(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
        body.put("error", "El cuerpo de la petición supera el tamaño máximo permitido.");
        mapper.writeValue(response.getWriter(), body);
    }

    /** Marca que el body superó el límite mientras se leía (nunca expone datos del body). */
    static final class RequestBodyTooLargeException extends IOException {
        RequestBodyTooLargeException(long limite) {
            super("El cuerpo de la petición supera el límite de " + limite + " bytes.");
        }
    }

    /** Envuelve el InputStream real para contar bytes leídos y cortar al superar el límite. */
    private static final class LimitedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final long limite;

        LimitedBodyRequestWrapper(HttpServletRequest request, long limite) {
            super(request);
            this.limite = limite;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitingServletInputStream(super.getInputStream(), limite);
        }
    }

    private static final class LimitingServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limite;
        private long leidos = 0;

        LimitingServletInputStream(ServletInputStream delegate, long limite) {
            this.delegate = delegate;
            this.limite = limite;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                contar(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) {
                contar(n);
            }
            return n;
        }

        private void contar(int n) throws IOException {
            leidos += n;
            if (leidos > limite) {
                throw new RequestBodyTooLargeException(limite);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
