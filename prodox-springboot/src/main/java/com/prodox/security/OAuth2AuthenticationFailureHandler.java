package com.prodox.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Maneja los fallos de autenticación OAuth2 (Google) que ocurren ANTES de
 * llegar a OAuth2AuthenticationSuccessHandler — ej. el usuario deniega el
 * consentimiento, se pierde la sesión con la authorization request entre la
 * ida y la vuelta a Google, o el intercambio del código falla.
 *
 * Sin este handler, Spring Security usa su comportamiento por defecto:
 * redirigir a "/login?error" en el propio backend, una ruta que no existe en
 * esta app (no hay .formLogin() configurado) — el usuario terminaba en una
 * página rota del backend en lugar de volver al frontend con un error
 * controlado. Este handler replica el mismo destino que ya usa el catch de
 * OAuth2AuthenticationSuccessHandler para que el frontend maneje ambos casos
 * de la misma forma.
 *
 * Autor: Cristian Santiago Martinez Cordoba — PRODOX
 */
@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${prodox.app.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.error("Fallo de autenticación OAuth2: {}", exception.getMessage());

        String errorUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/auth")
                .queryParam("error", "oauth_failed")
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, errorUrl);
    }
}
