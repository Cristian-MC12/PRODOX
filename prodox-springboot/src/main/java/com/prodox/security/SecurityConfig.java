package com.prodox.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2FailureHandler;

    @Value("${prodox.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register",
                        "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/project-members/invitacion/*").permitAll()
                // P0 seguridad: DevResetController solo existe como bean bajo
                // @Profile("dev") — sin ese perfil activo, Spring ni siquiera
                // registra la ruta y esta regla no tiene nada que proteger. Se
                // retira el permitAll global: si el perfil "dev" llegara a
                // activarse en un entorno menos controlado, /api/dev/** cae en
                // .anyRequest().authenticated() (la regla de cierre de abajo),
                // nunca queda abierto sin autenticación.
                .requestMatchers("/oauth2/**", "/login/oauth2/**", "/login/**").permitAll()
                .requestMatchers("/api/copiloto-plan/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
            )
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            // Bloque de seguridad HTTP (headers): X-Content-Type-Options=nosniff,
            // X-Frame-Options=DENY, Cache-Control/Pragma/Expires y X-XSS-Protection=0
            // ya los envía Spring Security 6 por defecto (verificado con peticiones
            // reales contra el backend corriendo — ver informe) y no se tocan acá.
            // Se agregan explícitamente los tres que SÍ faltaban:
            //
            // - Referrer-Policy: PRODOX es una API JSON pura (Spring Boot no sirve
            //   el HTML/JS/CSS de Angular — eso corre aparte en Vercel/Railway como
            //   servicio estático). strict-origin-when-cross-origin es la política
            //   por defecto recomendada: nunca filtra la URL completa a un origen
            //   distinto, solo el origen cuando corresponde.
            //
            // - Permissions-Policy: se deshabilitan explícitamente cámara, micrófono,
            //   geolocalización, pagos y fullscreen — ninguno se usa en todo
            //   prodox-angular (confirmado por auditoría: 0 referencias a
            //   getUserMedia/geolocation/requestFullscreen/Notification/Payment
            //   Request). navigator.clipboard.writeText() SÍ se usa (copiar código
            //   de invitación en equipo.component.ts) — deliberadamente NO se
            //   restringe clipboard-write.
            //
            // - Content-Security-Policy: default-src 'none' — la política más
            //   restrictiva posible, justificada porque este backend NUNCA sirve
            //   HTML/CSS/JS propios (confirmado: sin resource handlers, sin
            //   forward a index.html — ver EncodingConfig, el único WebMvcConfigurer
            //   del proyecto, que solo toca encoding). Sin unsafe-inline, sin
            //   unsafe-eval — no hacen falta porque no hay nada que ejecutar aquí.
            //   frame-ancestors 'none' refuerza X-Frame-Options: DENY (PRODOX no se
            //   embebe en iframes, confirmado: 0 usos de <iframe> en el frontend).
            //   base-uri/form-action 'none' por la misma razón: no hay documentos
            //   HTML propios cuya base o formularios proteger.
            //
            //   Esta CSP protege las respuestas del backend en sí (JSON, redirects
            //   de OAuth2, página whitelabel de error si alguna vez se navega
            //   directo a una URL rota). NO sustituye una CSP para el documento
            //   HTML de Angular — esa la debe emitir quien sirve ese HTML (Vercel/
            //   Railway estático), fuera del alcance de este repo backend; ver
            //   informe para el detalle de por qué la build de Angular (critical
            //   CSS inline vía Critters, sin nonce/hash) no sería compatible con
            //   una CSP estricta sin 'unsafe-inline' si se aplicara ahí.
            //
            // Strict-Transport-Security: NO se fuerza acá. El HstsHeaderWriter de
            // Spring Security ya está activo por defecto y solo escribe el header
            // cuando request.isSecure()==true — nunca en HTTP plano de desarrollo.
            // Si en producción (Railway, detrás de un proxy TLS-terminating) HSTS
            // no aparece, la causa más probable es que Spring no está interpretando
            // X-Forwarded-Proto (ver informe: server.forward-headers-strategy=
            // framework existe en el application-prod.properties LOCAL, pero no
            // está confirmado como variable de entorno real en Railway) — un cambio
            // de infraestructura, no de este archivo.
            .headers(headers -> {
                headers.referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                headers.permissionsPolicy(permissions -> permissions
                        .policy("camera=(), microphone=(), geolocation=(), payment=(), fullscreen=()"));
                headers.contentSecurityPolicy(csp -> csp
                        .policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"));
            })
            // Sin esto, Spring Security no tiene ningún AuthenticationEntryPoint
            // configurado (no hay .formLogin()/.httpBasic()) y por defecto responde
            // 403 también para una petición SIN autenticar o con un token inválido —
            // exactamente el mismo status que un SecurityException de negocio (ej.
            // SprintController.validarAcceso: "no sos miembro de este proyecto").
            // El frontend no puede distinguir "tu sesión no es válida" de "tu sesión
            // es válida pero no tenés acceso a ESTE recurso" — un 403 de autorización
            // sobre un recurso puntual terminaba cerrando la sesión completa. Este
            // entry point hace que "no autenticado" sea 401 (semántica HTTP correcta),
            // dejando 403 exclusivamente para fallos de autorización reales.
            .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public org.springframework.security.web.AuthenticationEntryPoint authenticationEntryPoint() {
        ObjectMapper mapper = new ObjectMapper();
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", Instant.now().toString());
            body.put("status", HttpStatus.UNAUTHORIZED.value());
            body.put("error", "No autenticado. Iniciá sesión nuevamente.");
            mapper.writeValue(response.getWriter(), body);
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
