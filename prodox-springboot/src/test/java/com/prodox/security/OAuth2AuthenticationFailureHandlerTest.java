package com.prodox.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth2AuthenticationFailureHandler — pruebas unitarias")
class OAuth2AuthenticationFailureHandlerTest {

    private OAuth2AuthenticationFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2AuthenticationFailureHandler();
        ReflectionTestUtils.setField(handler, "frontendUrl", "http://localhost:4200");
    }

    @Test
    @DisplayName("fallo de OAuth2 antes del successHandler: redirige al frontend con error controlado, no a /login del backend")
    void fallo_redirigeAlFrontendConErrorControlado() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response,
                new BadCredentialsException("authorization_request_not_found"));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/auth?error=oauth_failed");
    }
}
