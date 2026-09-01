package com.prodox.security;

import com.prodox.entity.AppUser;
import com.prodox.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2AuthenticationSuccessHandler — pruebas unitarias")
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock AppUserRepository appUserRepository;

    private JwtUtil jwtUtil;
    private OAuth2AuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "mpdia-test-secret-key-32chars-ok!");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3600000L);

        handler = new OAuth2AuthenticationSuccessHandler(appUserRepository, jwtUtil);
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

    private String tokenFromRedirect(String redirectedUrl) {
        int i = redirectedUrl.indexOf("token=");
        return redirectedUrl.substring(i + "token=".length());
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
        assertThat(response.getRedirectedUrl()).startsWith("http://localhost:4200/auth?token=");

        String token = tokenFromRedirect(response.getRedirectedUrl());
        assertThat(jwtUtil.getRole(token)).isEqualTo("scrum_member");
        assertThat(jwtUtil.getEmail(token)).isEqualTo("nuevo@prodox.com");
        assertThat(jwtUtil.getNombre(token)).isEqualTo("Nuevo");
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
        String token = tokenFromRedirect(response.getRedirectedUrl());
        assertThat(jwtUtil.getRole(token)).isEqualTo("scrum_master");
        assertThat(jwtUtil.getEmail(token)).isEqualTo("existente@prodox.com");
        assertThat(jwtUtil.getNombre(token)).isEqualTo("Nombre Original En BD");
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
        String token = tokenFromRedirect(response.getRedirectedUrl());
        assertThat(jwtUtil.getUserId(token)).isEqualTo(existente.getId().toString());
        assertThat(jwtUtil.getRole(token)).isEqualTo("scrum_master");
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
}
