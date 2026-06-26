package com.mpdia.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtUtil — pruebas unitarias")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // Clave mínima de 32 chars para HS256
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "mpdia-test-secret-key-32chars-ok!");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3600000L);
    }

    @Test
    @DisplayName("generateToken: genera un token JWT no vacío")
    void generateToken_datosValidos_retornaToken() {
        String token = jwtUtil.generateToken("uuid-123", "user@mpdia.com", "scrum_master");

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("getUserId: extrae el subject del token")
    void getUserId_tokenValido_retornaUserId() {
        String token = jwtUtil.generateToken("uuid-123", "user@mpdia.com", "scrum_master");
        assertThat(jwtUtil.getUserId(token)).isEqualTo("uuid-123");
    }

    @Test
    @DisplayName("getEmail: extrae el email del token")
    void getEmail_tokenValido_retornaEmail() {
        String token = jwtUtil.generateToken("uuid-123", "user@mpdia.com", "scrum_master");
        assertThat(jwtUtil.getEmail(token)).isEqualTo("user@mpdia.com");
    }

    @Test
    @DisplayName("getRole: extrae el rol del token")
    void getRole_tokenValido_retornaRole() {
        String token = jwtUtil.generateToken("uuid-123", "user@mpdia.com", "scrum_master");
        assertThat(jwtUtil.getRole(token)).isEqualTo("scrum_master");
    }

    @Test
    @DisplayName("isValid: retorna true con token válido")
    void isValid_tokenValido_retornaTrue() {
        String token = jwtUtil.generateToken("uuid-123", "user@mpdia.com", "scrum_member");
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("isValid: retorna false con token manipulado")
    void isValid_tokenInvalido_retornaFalse() {
        assertThat(jwtUtil.isValid("token.invalido.aqui")).isFalse();
    }

    @Test
    @DisplayName("isValid: retorna false con token vacío")
    void isValid_tokenVacio_retornaFalse() {
        assertThat(jwtUtil.isValid("")).isFalse();
    }

    @Test
    @DisplayName("token expirado: isValid retorna false")
    void isValid_tokenExpirado_retornaFalse() {
        JwtUtil expiredUtil = new JwtUtil();
        ReflectionTestUtils.setField(expiredUtil, "secret",
                "mpdia-test-secret-key-32chars-ok!");
        ReflectionTestUtils.setField(expiredUtil, "expirationMs", -1000L); // ya expirado

        String expiredToken = expiredUtil.generateToken("uuid", "user@mpdia.com", "scrum_member");
        assertThat(jwtUtil.isValid(expiredToken)).isFalse();
    }
}
