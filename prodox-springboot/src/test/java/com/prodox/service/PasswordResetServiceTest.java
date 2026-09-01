package com.prodox.service;

import com.prodox.entity.AppUser;
import com.prodox.entity.PasswordResetToken;
import com.prodox.repository.AppUserRepository;
import com.prodox.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetService — pruebas unitarias")
class PasswordResetServiceTest {

    @Mock AppUserRepository userRepo;
    @Mock PasswordResetTokenRepository tokenRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;

    PasswordResetService service;

    private UUID userId;
    private AppUser usuario;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepo, tokenRepo, passwordEncoder, emailService);
        ReflectionTestUtils.setField(service, "expirationMinutes", 30L);
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:4200");

        userId = UUID.randomUUID();
        usuario = new AppUser();
        usuario.setId(userId);
        usuario.setEmail("usuario@prodox.com");
        usuario.setPasswordHash("hash_bcrypt_anterior");
        usuario.setRole("scrum_master");
    }

    // ── solicitarRecuperacion ────────────────────────────────────────────

    @Test
    @DisplayName("solicitarRecuperacion: email existente genera token y envía correo con el enlace")
    void solicitarRecuperacion_emailExistente_generaTokenYEnviaCorreo() {
        when(userRepo.findByEmail("usuario@prodox.com")).thenReturn(Optional.of(usuario));
        when(emailService.enviar(anyString(), anyString(), anyString())).thenReturn(true);

        service.solicitarRecuperacion("usuario@prodox.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepo).save(captor.capture());
        PasswordResetToken saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getToken()).isNotBlank();
        assertThat(saved.getUsado()).isFalse();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("usuario@prodox.com"), anyString(), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("/reset-password?token=" + saved.getToken());
    }

    @Test
    @DisplayName("solicitarRecuperacion: email inexistente no genera token ni envía correo (pero no lanza excepción — respuesta pública siempre genérica)")
    void solicitarRecuperacion_emailInexistente_noHaceNadaNiLanzaExcepcion() {
        when(userRepo.findByEmail("noexiste@prodox.com")).thenReturn(Optional.empty());

        assertThatCode(() -> service.solicitarRecuperacion("noexiste@prodox.com")).doesNotThrowAnyException();

        verify(tokenRepo, never()).save(any());
        verify(emailService, never()).enviar(anyString(), anyString(), anyString());
    }

    // ── restablecerContrasena ────────────────────────────────────────────

    @Test
    @DisplayName("restablecerContrasena: token válido actualiza la contraseña con bcrypt, invalida el token y no toca el rol")
    void restablecerContrasena_tokenValido_actualizaPasswordConBcryptYNoTocaRol() {
        PasswordResetToken t = new PasswordResetToken();
        t.setUserId(userId);
        t.setToken("token-valido");
        t.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        t.setUsado(false);

        when(tokenRepo.findByToken("token-valido")).thenReturn(Optional.of(t));
        when(userRepo.findById(userId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("nuevaPassword123")).thenReturn("hash_bcrypt_nuevo");

        service.restablecerContrasena("token-valido", "nuevaPassword123");

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hash_bcrypt_nuevo");
        assertThat(userCaptor.getValue().getRole()).isEqualTo("scrum_master"); // rol intacto

        assertThat(t.getUsado()).isTrue();
        verify(tokenRepo).save(t);
    }

    @Test
    @DisplayName("restablecerContrasena: token inexistente lanza excepción 'Token de recuperación inválido'")
    void restablecerContrasena_tokenInexistente_lanzaExcepcion() {
        when(tokenRepo.findByToken("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restablecerContrasena("no-existe", "nuevaPassword123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inválido");
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("restablecerContrasena: token ya utilizado lanza excepción específica y no permite reutilizarlo")
    void restablecerContrasena_tokenYaUtilizado_lanzaExcepcion() {
        PasswordResetToken t = new PasswordResetToken();
        t.setUserId(userId);
        t.setToken("token-usado");
        t.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        t.setUsado(true);

        when(tokenRepo.findByToken("token-usado")).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.restablecerContrasena("token-usado", "nuevaPassword123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya fue utilizado");
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("restablecerContrasena: token expirado lanza excepción específica")
    void restablecerContrasena_tokenExpirado_lanzaExcepcion() {
        PasswordResetToken t = new PasswordResetToken();
        t.setUserId(userId);
        t.setToken("token-expirado");
        t.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        t.setUsado(false);

        when(tokenRepo.findByToken("token-expirado")).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.restablecerContrasena("token-expirado", "nuevaPassword123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiró");
        verify(userRepo, never()).save(any());
    }
}
