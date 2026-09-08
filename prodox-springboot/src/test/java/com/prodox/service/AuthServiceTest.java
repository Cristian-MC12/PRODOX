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

    // ── C4: límite de 72 bytes UTF-8 en contraseña — SOLO al crear (register) ──

    @Test
    @DisplayName("register: contraseña de exactamente 72 bytes ASCII es permitida")
    void register_passwordExactamente72BytesAscii_permitido() {
        String password72Bytes = "a".repeat(72);
        when(userRepository.existsByEmail("test@prodox.com")).thenReturn(false);
        when(passwordEncoder.encode(password72Bytes)).thenReturn("hash_bcrypt");
        when(userRepository.save(any(AppUser.class))).thenReturn(usuario);
        when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("jwt.token.test");

        AuthRequest request = new AuthRequest("test@prodox.com", password72Bytes, "scrum_master", "Test Usuario");

        assertThatCode(() -> authService.register(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("register: contraseña de 73 bytes ASCII es rechazada sin exponer la contraseña en el mensaje")
    void register_passwordMasDe72BytesAscii_lanzaExcepcionSinExponerLaPassword() {
        String password73Bytes = "a".repeat(73);
        when(userRepository.existsByEmail("test@prodox.com")).thenReturn(false);

        AuthRequest request = new AuthRequest("test@prodox.com", password73Bytes, "scrum_master", "Test Usuario");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 bytes")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(password73Bytes));

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: contraseña multibyte con <=72 CARACTERES pero >72 BYTES es rechazada")
    void register_passwordMultibyteMenosDe72CaracteresPeroMasDe72Bytes_lanzaExcepcion() {
        // "ñ" ocupa 2 bytes en UTF-8: 40 caracteres = 80 bytes (>72), pero
        // solo 40 caracteres (<=72) — un @Size(max=72) por caracteres NO
        // habría detectado este caso.
        String password40Caracteres80Bytes = "ñ".repeat(40);
        assertThat(password40Caracteres80Bytes.length()).isEqualTo(40);
        assertThat(password40Caracteres80Bytes.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isEqualTo(80);

        when(userRepository.existsByEmail("test@prodox.com")).thenReturn(false);

        AuthRequest request = new AuthRequest("test@prodox.com", password40Caracteres80Bytes, "scrum_master", "Test Usuario");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 bytes");

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("register: contraseña multibyte de exactamente 72 bytes es permitida")
    void register_passwordMultibyteExactamente72Bytes_permitido() {
        // 36 caracteres "ñ" × 2 bytes = exactamente 72 bytes.
        String password36Caracteres72Bytes = "ñ".repeat(36);
        assertThat(password36Caracteres72Bytes.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isEqualTo(72);

        when(userRepository.existsByEmail("test@prodox.com")).thenReturn(false);
        when(passwordEncoder.encode(password36Caracteres72Bytes)).thenReturn("hash_bcrypt");
        when(userRepository.save(any(AppUser.class))).thenReturn(usuario);
        when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("jwt.token.test");

        AuthRequest request = new AuthRequest("test@prodox.com", password36Caracteres72Bytes, "scrum_master", "Test Usuario");

        assertThatCode(() -> authService.register(request)).doesNotThrowAnyException();
    }

    // ── login ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("C4: login con contraseña de más de 72 bytes NO introduce ninguna restricción nueva (no rompe cuentas existentes)")
    void login_passwordMasDe72Bytes_noIntroduceRestriccionDeLongitud() {
        String passwordLarga = "a".repeat(200); // muy por encima de 72 bytes
        when(userRepository.findByEmail("test@prodox.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(passwordLarga, "hash_bcrypt")).thenReturn(true);
        when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("jwt.token.test");

        AuthRequest request = new AuthRequest("test@prodox.com", passwordLarga, null, null);

        // Ni excepción de longitud ni ningún otro efecto: sigue llegando hasta
        // passwordEncoder.matches() exactamente igual que antes de C4.
        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt.token.test");
        verify(passwordEncoder).matches(passwordLarga, "hash_bcrypt");
    }

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
