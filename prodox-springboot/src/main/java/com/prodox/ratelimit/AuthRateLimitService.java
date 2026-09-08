// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiting para los endpoints públicos de autenticación
 * (login/register/forgot-password/reset-password) — bloque de seguridad
 * independiente del RateLimitService existente (AI Copilot, keyed por
 * userId del JWT — inaplicable aquí porque estos endpoints se llaman ANTES
 * de que exista un JWT).
 *
 * Mismo patrón probado que RateLimitService: ventana deslizante en memoria,
 * ConcurrentHashMap + Deque por clave, sin dependencias nuevas.
 *
 * ⚠️ PROTECCIÓN POR INSTANCIA, NO DISTRIBUIDA: cada réplica del backend
 * mantiene su propio estado en memoria, sin coordinación entre instancias.
 * Si Railway llegara a correr más de una instancia del backend
 * simultáneamente, el límite efectivo se multiplica por la cantidad de
 * instancias (un atacante que alterne entre instancias vería, en la
 * práctica, límite×instancias en vez del límite configurado). No hay
 * evidencia en RAILWAY_DEPLOYMENT.md de que el despliegue actual use más de
 * una instancia — si eso cambia, esta protección deja de ser confiable tal
 * cual y requeriría un store compartido (Redis u otro) — deliberadamente
 * NO se agrega en este bloque (fuera de alcance, sin dependencias nuevas).
 *
 * ⚠️ SOBRE LA IP DEL CLIENTE: se usa exclusivamente
 * HttpServletRequest.getRemoteAddr() (la IP real de la conexión TCP que
 * llega al proceso Java) — NUNCA el header X-Forwarded-For. Verificado
 * antes de implementar: server.forward-headers-strategy=framework (que
 * haría que Tomcat reescriba getRemoteAddr() a partir de X-Forwarded-For)
 * SOLO existe en application-prod.properties LOCAL, no trackeado en git, sin
 * evidencia de estar configurado como variable de entorno real en Railway
 * (RAILWAY_DEPLOYMENT.md no lo menciona). Sin esa confirmación, confiar en
 * X-Forwarded-For sería confiar en un header que, si el backend fuera
 * alcanzable de cualquier otra forma que no sea exclusivamente a través del
 * proxy de Railway, cualquier cliente podría falsificar libremente para
 * evadir el límite. getRemoteAddr() nunca puede falsificarse por el
 * cliente — el costo de esta elección conservadora es que, si Railway
 * efectivamente enruta múltiples clientes distintos a través de una misma
 * IP interna de borde, el límite por IP podría agruparlos a todos bajo una
 * sola IP aparente (más estricto de lo ideal, nunca más permisivo). El
 * límite por email, independiente de la IP, sigue siendo efectivo en ese
 * escenario. Si en el futuro se confirma server.forward-headers-strategy
 * como variable real de Railway, esta clase puede revisarse para leer
 * getRemoteAddr() con confianza en que Tomcat ya lo resolvió correctamente
 * — no hace falta tocar X-Forwarded-For directamente en ningún caso.
 */
@Slf4j
@Service
public class AuthRateLimitService {

    @Value("${prodox.auth.rate-limit.login.max-per-email:10}")
    private int loginMaxPorEmail;
    @Value("${prodox.auth.rate-limit.login.max-per-ip:20}")
    private int loginMaxPorIp;
    @Value("${prodox.auth.rate-limit.login.window-seconds:300}")
    private int loginWindowSeconds;

    @Value("${prodox.auth.rate-limit.register.max-per-ip:5}")
    private int registerMaxPorIp;
    @Value("${prodox.auth.rate-limit.register.window-seconds:3600}")
    private int registerWindowSeconds;

    @Value("${prodox.auth.rate-limit.forgot-password.max-per-ip:5}")
    private int forgotMaxPorIp;
    @Value("${prodox.auth.rate-limit.forgot-password.max-per-email:5}")
    private int forgotMaxPorEmail;
    @Value("${prodox.auth.rate-limit.forgot-password.window-seconds:3600}")
    private int forgotWindowSeconds;

    @Value("${prodox.auth.rate-limit.reset-password.max-per-ip:20}")
    private int resetMaxPorIp;
    @Value("${prodox.auth.rate-limit.reset-password.window-seconds:3600}")
    private int resetWindowSeconds;

    /**
     * Cada clave combina el propósito ("login"/"register"/"forgot"/"reset"),
     * la dimensión ("ip"/"email") y el valor (IP real o hash SHA-256 del
     * email normalizado — nunca el email en texto plano: ver
     * normalizarYHashearEmail()). Nunca se guarda password, JWT ni token de
     * recuperación en ninguna clave ni valor de este mapa.
     */
    private final ConcurrentHashMap<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    /**
     * Evita crecimiento indefinido del mapa: a diferencia de RateLimitService
     * (keyed por userId, un universo acotado a la base de usuarios real),
     * acá las claves incluyen IPs de origen desconocido en endpoints
     * PÚBLICOS — cualquier tráfico de internet (escaneos, bots) crea una
     * entrada nueva. Cada N llamadas a intentar(), se barre el mapa
     * completo y se eliminan las claves cuya cola quedó vacía tras podar
     * los timestamps vencidos — sin depender de un @Scheduled adicional
     * (ya existe @EnableScheduling en ProdoxApplication si se quisiera
     * complementar, pero este mecanismo ya alcanza).
     */
    private static final long LIMPIEZA_CADA_N_LLAMADAS = 500;
    private final AtomicLong llamadas = new AtomicLong(0);

    // ── API pública — una verificación por endpoint ───────────────────────

    /** Login: exige estar dentro del límite por email Y por IP. */
    public void verificarLogin(String ip, String email) {
        boolean okIp = intentar(clave("login", "ip", ip), loginMaxPorIp, loginWindowSeconds);
        boolean okEmail = intentar(clave("login", "email", normalizarYHashearEmail(email)), loginMaxPorEmail, loginWindowSeconds);
        if (!okIp || !okEmail) {
            log.warn("RateLimit auth: login bloqueado (ip={}, límiteEmailSuperado={})", ip, !okEmail);
            throw new RateLimitException("Demasiados intentos de inicio de sesión. Intenta nuevamente más tarde.");
        }
    }

    /** Register: exige estar dentro del límite por IP. */
    public void verificarRegistro(String ip) {
        if (!intentar(clave("register", "ip", ip), registerMaxPorIp, registerWindowSeconds)) {
            log.warn("RateLimit auth: registro bloqueado (ip={})", ip);
            throw new RateLimitException("Demasiados intentos de registro. Intenta nuevamente más tarde.");
        }
    }

    /**
     * Forgot-password: exige estar dentro del límite por IP Y por email.
     * DEBE llamarse ANTES de consultar si el usuario existe (así lo hace
     * AuthController) — el contador por email se incrementa exista o no la
     * cuenta, así que el 429 nunca revela existencia: un atacante probando
     * emails inventados agota su propio límite exactamente igual que uno
     * probando emails reales.
     */
    public void verificarForgotPassword(String ip, String email) {
        boolean okIp = intentar(clave("forgot", "ip", ip), forgotMaxPorIp, forgotWindowSeconds);
        boolean okEmail = intentar(clave("forgot", "email", normalizarYHashearEmail(email)), forgotMaxPorEmail, forgotWindowSeconds);
        if (!okIp || !okEmail) {
            log.warn("RateLimit auth: forgot-password bloqueado (ip={})", ip);
            throw new RateLimitException("Demasiadas solicitudes de recuperación de contraseña. Intenta nuevamente más tarde.");
        }
    }

    /** Reset-password: exige estar dentro del límite por IP. */
    public void verificarResetPassword(String ip) {
        if (!intentar(clave("reset", "ip", ip), resetMaxPorIp, resetWindowSeconds)) {
            log.warn("RateLimit auth: reset-password bloqueado (ip={})", ip);
            throw new RateLimitException("Demasiados intentos de restablecimiento de contraseña. Intenta nuevamente más tarde.");
        }
    }

    // ── Mecanismo genérico de ventana deslizante ──────────────────────────

    /**
     * true si la clave todavía tiene cupo dentro de la ventana (y registra
     * este intento); false si ya alcanzó el máximo (el intento bloqueado NO
     * se registra — no cuenta contra ventanas futuras).
     */
    private boolean intentar(String key, int max, int windowSeconds) {
        if (llamadas.incrementAndGet() % LIMPIEZA_CADA_N_LLAMADAS == 0) {
            limpiarClavesVacias();
        }

        Instant ahora = Instant.now();
        Instant inicioVentana = ahora.minusSeconds(windowSeconds);
        Deque<Instant> cola = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (cola) {
            podar(cola, inicioVentana);
            if (cola.size() >= max) {
                return false;
            }
            cola.addLast(ahora);
            return true;
        }
    }

    private void podar(Deque<Instant> cola, Instant inicioVentana) {
        while (!cola.isEmpty() && cola.peekFirst().isBefore(inicioVentana)) {
            cola.pollFirst();
        }
    }

    private void limpiarClavesVacias() {
        int antes = buckets.size();
        buckets.entrySet().removeIf(entry -> {
            Deque<Instant> cola = entry.getValue();
            synchronized (cola) {
                // Poda con la ventana más larga configurada, para no borrar
                // una cola que todavía tiene entradas vigentes bajo alguna
                // de las ventanas en uso (login/register/forgot/reset).
                podar(cola, Instant.now().minusSeconds(ventanaMasLarga()));
                return cola.isEmpty();
            }
        });
        int despues = buckets.size();
        if (antes != despues) {
            log.debug("RateLimit auth: limpieza removió {} claves vacías ({} -> {})", antes - despues, antes, despues);
        }
    }

    private int ventanaMasLarga() {
        return Math.max(Math.max(loginWindowSeconds, registerWindowSeconds),
                Math.max(forgotWindowSeconds, resetWindowSeconds));
    }

    private String clave(String proposito, String dimension, String valor) {
        return proposito + ':' + dimension + ':' + valor;
    }

    /**
     * Normaliza (trim + minúsculas) y hashea con SHA-256 el email antes de
     * usarlo como clave — nunca se guarda el email en texto plano en este
     * componente, ni siquiera en memoria transitoria de las claves del mapa.
     */
    private String normalizarYHashearEmail(String email) {
        String normalizado = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizado.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    // ── Soporte de testing (mismo patrón que RateLimitService.resetAll()) ──

    /** Limpia todo el estado — uso exclusivo de tests, para aislar cada caso. */
    public void resetAll() {
        buckets.clear();
        llamadas.set(0);
    }

    /** Cantidad de claves activas en el mapa — uso exclusivo de tests (verificar limpieza). */
    public int clavesActivas() {
        return buckets.size();
    }
}
