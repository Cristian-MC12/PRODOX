package com.mpdia.controller;

import com.mpdia.ratelimit.RateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Errores de validación de negocio (IllegalArgumentException en services) */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * FASE 19: Gemini no pudo generar una propuesta de métrica con IA (503/429/
     * timeout/respuesta no parseable). HTTP 503 refleja que la dependencia externa
     * (Gemini) no está disponible en este momento — nunca se traduce en un 200
     * con una propuesta disfrazada de válida.
     */
    @ExceptionHandler(com.mpdia.service.PropuestaIANoDisponibleException.class)
    public ResponseEntity<Map<String, Object>> handlePropuestaIANoDisponible(
            com.mpdia.service.PropuestaIANoDisponibleException ex) {
        log.warn("Propuesta de IA no disponible: {}", ex.getMessage());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    /**
     * FASE 23: Gemini no pudo generar un reporte ejecutivo de sprint (error
     * HTTP del proveedor, cuota agotada, respuesta vacía o no interpretable).
     * HTTP 503 refleja que la dependencia externa (Gemini) no está disponible
     * en este momento — nunca se traduce en un 200 con un reporte disfrazado
     * de válido. Ver AIReportService.generateReport().
     */
    @ExceptionHandler(com.mpdia.service.ReporteIANoDisponibleException.class)
    public ResponseEntity<Map<String, Object>> handleReporteIANoDisponible(
            com.mpdia.service.ReporteIANoDisponibleException ex) {
        log.warn("Reporte de IA no disponible: {}", ex.getMessage());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    /**
     * FASE 24: mismo defecto que ReporteIANoDisponibleException (FASE 23),
     * confirmado en vivo para AIRetrospectiveService.generateRetrospective().
     */
    @ExceptionHandler(com.mpdia.service.RetrospectivaIANoDisponibleException.class)
    public ResponseEntity<Map<String, Object>> handleRetrospectivaIANoDisponible(
            com.mpdia.service.RetrospectivaIANoDisponibleException ex) {
        log.warn("Retrospectiva de IA no disponible: {}", ex.getMessage());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    /** Conflictos de estado (IllegalStateException) */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Validación de @Valid en DTOs */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "inválido",
                        (a, b) -> a
                ));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Error de validación");
        body.put("campos", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /** JSON mal formado o body no legible */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es válido o tiene un formato incorrecto.");
    }

    /** Parámetro de ruta con tipo incorrecto (ej: UUID inválido) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = "El parámetro '" + ex.getName() + "' tiene un formato inválido.";
        return buildResponse(HttpStatus.BAD_REQUEST, msg);
    }

    /** Parámetro requerido faltante */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Falta el parámetro requerido: " + ex.getParameterName());
    }

    /** Violación de integridad en BD (unique, FK, etc.) */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return buildResponse(HttpStatus.CONFLICT, "Operación rechazada: conflicto de datos (posible duplicado o referencia inválida).");
    }

    /** Acceso denegado (sin permisos) */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta acción.");
    }

    /** Security exception de servicios (sin acceso a recursos específicos) */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /** Rate limit excedido - HTTP 429 TOO_MANY_REQUESTS */
    @ExceptionHandler(com.mpdia.ratelimit.RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitException(com.mpdia.ratelimit.RateLimitException ex) {
        log.warn("Rate limit excedido: {}", ex.getMessage());
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    /** Recurso/ruta no encontrado */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoResourceFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "Recurso no encontrado: " + ex.getResourcePath());
    }

    /** Cualquier otra excepción no controlada */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Error no controlado: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor. Intenta de nuevo más tarde.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
