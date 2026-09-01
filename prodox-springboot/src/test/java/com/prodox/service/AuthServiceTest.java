package com.prodox.service;

import com.prodox.dto.AuthRequest;
import com.prodox.dto.AuthResponse;
import com.prodox.entity.AppUser;
import com.prodox.repository.AppUserRepository;
import com.prodox.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — pruebas unitarias")
class AuthServiceTest {

    @Mock AppUserRepository userRepository;
    @Mock PasswordEncoder   passwordEncoder;
    @Mock JwtUtil           jwtUtil;

    @InjectMocks AuthService authService;

    private AppUser usuario;

    @BeforeEach
    void setUp() {
        usuario = new AppUser();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail("test@prodox.com");
        usuario.setPasswordHash("hash_bcrypt");
        usuario.setRole("scrum_master");
        usuario.setNombre("Test Usuario");
    }

    // ── register ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: registra usuario con rol scrum_master")
    void register_conRolValido_retornaAuthResponse() {
        when(userRepository.existsByEmail("test@prodox.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash_bcrypt");
        when(userRepository.save(any(AppUser.class))).thenReturn(usuario);
        when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("jwt.token.test");

        AuthRequest request = new AuthRequest("test@prodox.com", "password123", "scrum_master", "Test Usuario");
        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt.token.test");
        assertThat(response.email()).isEqualTo("test@prodox.com");
        assertThat(response.role()).isEqualTo("scrum_master");
        assertThat(response.nombre()).isEqualTo("Test Usuario");
        verify(userRepository).save(argThat(u -> "Test Usuario".equals(u.getNombre())));
    }

    @Test
    @DisplayName("register: asigna rol scrum_member cuando el rol es inválido")
    void register_conRolInvalido_asignaScrumMember() {
        AppUser userMember = new AppUser();
        userMember.setId(UUID.randomUUID());
        userMember.setEmail("member@prodox.com");
        userMember.setPasswordHash("hash");
        userMember.setRole("scrum_member");

        when(userRepository.existsByEmail("member@prodox.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(AppUser.class))).thenReturn(userMember);
        when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("token");

        AuthRequest request = new AuthRequest("member@prodox.com", "password123", "rol_invalido", null);
        AuthResponse response = authService.register(request);

        assertThat(response.role()).isEqualTo("scrum_member");
    }

    @Test
    @DisplayName("register: lanza excepción si el correo ya está registrado")
    void register_emailDuplicado_lanzaExcepcion() {
        when(userRepository.existsByEmail("test@prodox.com")).thenReturn(true);

        AuthRequest request = new AuthRequest("test@prodox.com", "password123", "scrum_master", "Test Usuario");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya está registrado");
    }

    // ── login ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: retorna token con credenciales correctas")
    void login_credencialesCorrectas_retornaAuthResponse() {
        when(userRepository.findByEmail("test@prodox.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("password123", "hash_bcrypt")).thenReturn(true);
        when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("jwt.token.test");

        AuthRequest request = new AuthRequest("test@prodox.com", "password123", null, null);
        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt.token.test");
        assertThat(response.email()).isEqualTo("test@prodox.com");
        assertThat(response.nombre()).isEqualTo("Test Usuario");
    }

    @Test
    @DisplayName("login: lanza excepción si el usuario no existe")
    void login_usuarioNoExiste_lanzaExcepcion() {
        when(userRepository.findByEmail("noexiste@prodox.com")).thenReturn(Optional.empty());

        AuthRequest request = new AuthRequest("noexiste@prodox.com", "pass", null, null);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Credenciales inválidas");
    }

    @Test
    @DisplayName("login: lanza excepción si la contraseña es incorrecta")
    void login_passwordIncorrecta_lanzaExcepcion() {
        when(userRepository.findByEmail("test@prodox.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("wrong_pass", "hash_bcrypt")).thenReturn(false);

        AuthRequest request = new AuthRequest("test@prodox.com", "wrong_pass", null, null);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Credenciales inválidas");
    }
}
