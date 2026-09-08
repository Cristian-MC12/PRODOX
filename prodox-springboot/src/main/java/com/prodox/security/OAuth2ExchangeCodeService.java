// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bloque de seguridad JWT/OAuth2 — corrige la exposición del JWT completo en
 * la URL de redirect y en logs (OAuth2AuthenticationSuccessHandler): en vez
 * de redirigir con el JWT real, se emite un código opaco de un solo uso, de
 * vida muy corta, que el frontend canjea por el JWT real mediante un POST
 * (cuerpo, no URL) — ver AuthController.exchangeOAuth2Code().
 *
 * Mismo patrón ya usado y aprobado en este proyecto (RateLimitService,
 * AuthRateLimitService): ConcurrentHashMap en memoria, sin dependencias
 * nuevas, sin Redis.
 *
 * ⚠️ ALMACENAMIENTO POR INSTANCIA: el código temporal vive en memoria de ESTA
 * instancia del backend. Si Railway ejecuta múltiples instancias del
 * backend, el código generado en una instancia puede no estar disponible en
 * otra. La solución distribuida (store compartido tipo Redis) queda fuera de
 * este bloque — no hay evidencia de despliegue multi-instancia actual (ver
 * RAILWAY_DEPLOYMENT.md).
 *
 * El código NUNCA es un sustituto de sesión: es exclusivamente el vehículo
 * de un solo uso para entregar el JWT real inmediatamente después del
 * redirect — se consume (se elimina) en el primer canje exitoso, exista o
 * no todavía dentro de su ventana de vida, y nunca puede volver a canjearse.
 */
@Slf4j
@Service
public class OAuth2ExchangeCodeService {

    @Value("${prodox.oauth2.exchange.ttl-seconds:60}")
    private long ttlSeconds;

    /** 256 bits — mismo tamaño que PasswordResetService.generarToken(), no un UUID (128 bits, predecible en su estructura). */
    private static final int CODE_BYTES = 32;

    private static final long LIMPIEZA_CADA_N_LLAMADAS = 200;
    private final AtomicLong llamadas = new AtomicLong(0);

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Entrada> codigos = new ConcurrentHashMap<>();

    private record Entrada(String jwt, Instant expiraEn) {}

    /**
     * Genera un código opaco nuevo, aleatorio y de un solo uso, y lo asocia
     * al JWT ya generado (nunca se loguea ni el código ni el JWT acá).
     */
    public String emitirCodigo(String jwt) {
        if (llamadas.incrementAndGet() % LIMPIEZA_CADA_N_LLAMADAS == 0) {
            limpiarExpirados();
        }
        byte[] bytes = new byte[CODE_BYTES];
        random.nextBytes(bytes);
        String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codigos.put(codigo, new Entrada(jwt, Instant.now().plusSeconds(ttlSeconds)));
        return codigo;
    }

    /**
     * Canje atómico: el código se elimina del mapa en la misma operación que
     * lo lee (ConcurrentHashMap.remove es atómico) — de dos hilos que
     * intenten canjear el MISMO código al mismo tiempo, exactamente uno
     * obtiene la entrada (y por lo tanto el JWT); el otro recibe Optional
     * vacío, sin ninguna sincronización manual adicional. Un código expirado
     * también se elimina (nunca queda "reutilizable" aunque haya expirado
     * sin usarse) pero no devuelve el JWT.
     */
    public Optional<String> canjear(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        Entrada entrada = codigos.remove(codigo);
        if (entrada == null) {
            return Optional.empty();
        }
        if (entrada.expiraEn().isBefore(Instant.now())) {
            log.info("OAuth2 exchange: código expirado (metadata: ventana de {}s)", ttlSeconds);
            return Optional.empty();
        }
        return Optional.of(entrada.jwt());
    }

    /** Evita crecimiento indefinido por códigos emitidos y nunca canjeados (flujo OAuth2 abandonado a mitad de camino). */
    private void limpiarExpirados() {
        Instant ahora = Instant.now();
        int antes = codigos.size();
        codigos.entrySet().removeIf(e -> e.getValue().expiraEn().isBefore(ahora));
        int despues = codigos.size();
        if (antes != despues) {
            log.debug("OAuth2 exchange: limpieza removió {} códigos expirados sin canjear ({} -> {})", antes - despues, antes, despues);
        }
    }

    /** Uso exclusivo de tests. */
    public int codigosActivos() {
        return codigos.size();
    }

    /** Uso exclusivo de tests — limpia todo el estado entre casos. */
    public void resetAll() {
        codigos.clear();
        llamadas.set(0);
    }
}
