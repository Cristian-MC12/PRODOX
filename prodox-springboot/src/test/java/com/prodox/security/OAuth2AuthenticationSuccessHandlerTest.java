package com.prodox.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.prodox.entity.AppUser;
import com.prodox.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bloque de seguridad JWT/OAuth2: desde esta corrección, el handler ya NO
 * pone el JWT en la URL de redirect — emite un código opaco de un solo uso
 * (OAuth2ExchangeCodeService, real acá, no mockeado) y el JWT solo se
 * obtiene canjeando ese código, exactamente como lo haría
 * AuthController.exchangeOAuth2Code() en producción.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2AuthenticationSuccessHandler — pruebas unitarias")
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock AppUserRepository appUserRepository;

    private JwtUtil jwtUtil;
    private OAuth2ExchangeCodeService exchangeCodeService;
    private OAuth2AuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "mpdia-test-secret-key-32chars-ok!");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3600000L);

        exchangeCodeService = new OAuth2ExchangeCodeService();
        ReflectionTestUtils.setField(exchangeCodeService, "ttlSeconds", 60L);

        handler = new OAuth2AuthenticationSuccessHandler(appUserRepository, jwtUtil, exchangeCodeService);
        ReflectionTestUtils.setField(handler, "frontendUrl", "http://localhost:4200");
    }

    private OAuth2User googleUser(String email, String name) {
        Map<String, Object> attrs = Map.of(
                "email", email,
                "name", name,
                "picture", "http://pic.example/x.png",
                "sub", "google-sub-id"
        );
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, "sub");
    }

    private String codeFromRedirect(String redirectedUrl) {
        int i = redirectedUrl.indexOf("code=");
        return redirectedUrl.substring(i + "code=".length());
    }

    /** Canjea el código del redirect por el JWT real — mismo paso que hace AuthController.exchangeOAuth2Code(). */
    private String jwtFromRedirect(String redirectedUrl) {
        String code = codeFromRedirect(redirectedUrl);
        return exchangeCodeService.canjear(code)
                .orElseThrow(() -> new AssertionError("El código emitido en el redirect debería canjearse por un JWT válido"));
    }

    // ── usuario nuevo ────────────────────────────────────────────────────

    @Test
    @DisplayName("usuario Google nuevo: se crea con rol scrum_member (rol seguro por defecto del sistema, no 'developer')")
    void usuarioNuevo_seCreaConRolScrumMember() throws Exception {
        when(appUserRepository.findByEmail("nuevo@prodox.com")).thenReturn(Optional.empty());

        AppUser saved = new AppUser();
        saved.setId(UUID.randomUUID());
        saved.setEmail("nuevo@prodox.com");
        saved.setRole("scrum_member");
        saved.setNombre("Nuevo");
        when(appUserRepository.save(any(AppUser.class))).thenReturn(saved);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication auth = new TestingAuthenticationToken(googleUser("nuevo@prodox.com", "Nuevo"), null);

        handler.onAuthenticationSuccess(request, response, auth);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("scrum_member");
        assertThat(captor.getValue().getNombre()).isEqualTo("Nuevo");
        assertThat(response.getRedirectedUrl()).startsWith("http://localhost:4200/auth?code=");

        String jwt = jwtFromRedirect(response.getRedirectedUrl());
        assertThat(jwtUtil.getRole(jwt)).isEqualTo("scrum_member");
        assertThat(jwtUtil.getEmail(jwt)).isEqualTo("nuevo@prodox.com");
        assertThat(jwtUtil.getNombre(jwt)).isEqualTo("Nuevo");
    }

    // ── usuario existente ───────────────────────────────────────────────

    @Test
    @DisplayName("usuario Google existente: conserva su rol y su nombre tal cual están en BD, no se duplica ni se sobrescriben con los datos de Google")
    void usuarioExistente_conservaRolYNoDuplica() throws Exception {
        AppUser existente = new AppUser();
        existente.setId(UUID.randomUUID());
        existente.setEmail("existente@prodox.com");
        existente.setRole("scrum_master");
        existente.setNombre("Nombre Original En BD");
        when(appUserRepository.findByEmail("existente@prodox.com")).thenReturn(Optional.of(existente));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        // Google reporta un nombre distinto ("Nombre Distinto En Google") — no debe
        // pisar el que ya está guardado en BD.
        Authentication auth = new TestingAuthenticationToken(googleUser("existente@prodox.com", "Nombre Distinto En Google"), null);

        handler.onAuthenticationSuccess(request, response, auth);

        verify(appUserRepository, never()).save(any());
        String jwt = jwtFromRedirect(response.getRedirectedUrl());
        assertThat(jwtUtil.getRole(jwt)).isEqualTo("scrum_master");
        assertThat(jwtUtil.getEmail(jwt)).isEqualTo("existente@prodox.com");
        assertThat(jwtUtil.getNombre(jwt)).isEqualTo("Nombre Original En BD");
    }

    // ── usuario que ya existía por email/contraseña ─────────────────────

    @Test
    @DisplayName("usuario creado antes con email/contraseña: al entrar por Google usa la misma cuenta y su mismo rol")
    void usuarioCreadoPorPassword_alUsarGoogleConservaSuCuenta() throws Exception {
        AppUser existente = new AppUser();
        existente.setId(UUID.randomUUID());
        existente.setEmail("mixto@prodox.com");
        existente.setPasswordHash("hash_bcrypt_real");
        existente.setRole("scrum_master");
        when(appUserRepository.findByEmail("mixto@prodox.com")).thenReturn(Optional.of(existente));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication auth = new TestingAuthenticationToken(googleUser("mixto@prodox.com", "Mixto"), null);

        handler.onAuthenticationSuccess(request, response, auth);

        verify(appUserRepository, never()).save(any());
        String jwt = jwtFromRedirect(response.getRedirectedUrl());
        assertThat(jwtUtil.getUserId(jwt)).isEqualTo(existente.getId().toString());
        assertThat(jwtUtil.getRole(jwt)).isEqualTo("scrum_master");
    }

    // ── fallo controlado ─────────────────────────────────────────────────

    @Test
    @DisplayName("error durante el procesamiento: redirige a /auth?error=oauth_failed en lugar de lanzar excepción o hacer loop")
    void errorEnProcesamiento_redirigeConErrorControlado() throws Exception {
        when(appUserRepository.findByEmail(any())).thenThrow(new RuntimeException("DB caída"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication auth = new TestingAuthenticationToken(googleUser("falla@prodox.com", "Falla"), null);

        handler.onAuthenticationSuccess(request, response, auth);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/auth?error=oauth_failed");
    }

    // ── Bloque de seguridad JWT/OAuth2 (esta corrección) ──────────────────

    private static final Pattern PATRON_JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    @Test
    @DisplayName("Seguridad: la URL de redirect NO contiene el JWT — ni 'token=', ni ningún fragmento con la forma de un JWT (eyJ...)")
    void redirectNuncaContieneElJwt() throws Exception {
        when(appUserRepository.findByEmail("segura@prodox.com")).thenReturn(Optional.empty());
        AppUser saved = new AppUser();
        saved.setId(UUID.randomUUID());
        saved.setEmail("segura@prodox.com");
        saved.setRole("scrum_member");
        when(appUserRepository.save(any(AppUser.class))).thenReturn(saved);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication auth = new TestingAuthenticationToken(googleUser("segura@prodox.com", "Segura"), null);

        handler.onAuthenticationSuccess(request, response, auth);

        String redirectedUrl = response.getRedirectedUrl();
        assertThat(redirectedUrl).doesNotContain("token=");
        assertThat(PATRON_JWT.matcher(redirectedUrl).find())
                .as("La URL de redirect no debe contener ningún string con forma de JWT: %s", redirectedUrl)
                .isFalse();
        assertThat(redirectedUrl).contains("code=");
    }

    @Test
    @DisplayName("Seguridad: el redirect usa un código opaco de un solo uso — canjearlo dos veces solo funciona la primera")
    void codigoDelRedirectEsDeUnSoloUso() throws Exception {
        when(appUserRepository.findByEmail("unsolouso@prodox.com")).thenReturn(Optional.empty());
        AppUser saved = new AppUser();
        saved.setId(UUID.randomUUID());
        saved.setEmail("unsolouso@prodox.com");
        saved.setRole("scrum_member");
        when(appUserRepository.save(any(AppUser.class))).thenReturn(saved);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication auth = new TestingAuthenticationToken(googleUser("unsolouso@prodox.com", "UnSoloUso"), null);
        handler.onAuthenticationSuccess(request, response, auth);

        String code = codeFromRedirect(response.getRedirectedUrl());

        assertThat(exchangeCodeService.canjear(code)).isPresent();
        assertThat(exchangeCodeService.canjear(code)).isEmpty(); // segundo canje del MISMO código: rechazado
    }

    @Test
    @DisplayName("Seguridad: ningún mensaje de log de este handler contiene el JWT completo (antes: log.info(\"Redirigiendo a: {}\", targetUrl) sí lo incluía)")
    void ningunLogContieneElJwtCompleto() throws Exception {
        Logger logbackLogger = (Logger) LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        try {
            when(appUserRepository.findByEmail("sinlogs@prodox.com")).thenReturn(Optional.empty());
            AppUser saved = new AppUser();
            saved.setId(UUID.randomUUID());
            saved.setEmail("sinlogs@prodox.com");
            saved.setRole("scrum_member");
            when(appUserRepository.save(any(AppUser.class))).thenReturn(saved);

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            Authentication auth = new TestingAuthenticationToken(googleUser("sinlogs@prodox.com", "SinLogs"), null);

            handler.onAuthenticationSuccess(request, response, auth);

            // codeFromRedirect() solo hace parsing de texto sobre la URL —
            // se puede leer el mismo código aunque jwtFromRedirect() ya lo
            // haya consumido del mapa (son dos lecturas independientes de
            // la misma URL, en cualquier orden).
            String code = codeFromRedirect(response.getRedirectedUrl());
            String jwt = jwtFromRedirect(response.getRedirectedUrl());

            for (ILoggingEvent event : appender.list) {
                String mensaje = event.getFormattedMessage();
                assertThat(mensaje).as("mensaje de log: %s", mensaje)
                        .doesNotContain(jwt)
                        .doesNotContain(code)
                        .doesNotContain("token=")
                        .matches(m -> !PATRON_JWT.matcher(m).find());
            }
            assertThat(appender.list).isNotEmpty(); // confirma que sí se generaron logs (no es un falso positivo por falta de eventos)
        } finally {
            logbackLogger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("Seguridad: el log de error (fallo de procesamiento) tampoco expone JWT/código — solo el mensaje de la excepción de negocio")
    void logDeErrorNoExponeCredenciales() throws Exception {
        Logger logbackLogger = (Logger) LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        try {
            when(appUserRepository.findByEmail(any())).thenThrow(new RuntimeException("DB caída"));

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            Authentication auth = new TestingAuthenticationToken(googleUser("fallalog@prodox.com", "FallaLog"), null);

            handler.onAuthenticationSuccess(request, response, auth);

            for (ILoggingEvent event : appender.list) {
                String mensaje = event.getFormattedMessage();
                assertThat(PATRON_JWT.matcher(mensaje).find())
                        .as("mensaje de log de error no debe contener un JWT: %s", mensaje)
                        .isFalse();
                assertThat(mensaje).doesNotContain("token=").doesNotContain("code=");
            }
        } finally {
            logbackLogger.detachAppender(appender);
        }
    }
}
