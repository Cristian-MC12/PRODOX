package com.prodox.security;

import com.prodox.entity.AppUser;
import com.prodox.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Optional;

/**
 * Handler para autenticación exitosa con Google OAuth 2.0
 * Autor: Cristian Santiago Martinez Cordoba — PRODOX
 *
 * Bloque de seguridad JWT/OAuth2: el JWT real de sesión NUNCA viaja en la URL
 * de este redirect ni en ningún log — se genera acá, pero solo se entrega al
 * frontend a través de OAuth2ExchangeCodeService: el redirect lleva
 * exclusivamente un código opaco de un solo uso y de vida muy corta
 * (?code=...), que el frontend canjea por el JWT real mediante POST
 * (AuthController.exchangeOAuth2Code(), cuerpo JSON, nunca URL). Antes de
 * esta corrección, el JWT completo viajaba tanto en la URL de este redirect
 * como en el log de "Redirigiendo a: {targetUrl}" — ambos puntos de
 * exposición se corrigen acá.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AppUserRepository appUserRepository;
    private final JwtUtil jwtUtil;
    private final OAuth2ExchangeCodeService exchangeCodeService;

    @Value("${prodox.app.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

            String email = oAuth2User.getAttribute("email");
            String name = oAuth2User.getAttribute("name");
            String picture = oAuth2User.getAttribute("picture");

            log.info("OAuth2 login exitoso para email: {}", email);

            // Buscar o crear usuario
            AppUser user = appUserRepository.findByEmail(email)
                    .orElseGet(() -> createNewOAuth2User(email, name, picture));

            // Generar JWT real (nunca se loguea, nunca viaja en esta URL) y
            // emitir en su lugar un código opaco de un solo uso para el redirect.
            String jwt = jwtUtil.generateToken(user.getId().toString(), user.getEmail(), user.getRole(), user.getNombre());
            String exchangeCode = exchangeCodeService.emitirCodigo(jwt);

            String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/auth")
                    .queryParam("code", exchangeCode)
                    .build()
                    .toUriString();

            // Log seguro: ni el JWT ni el código de intercambio se imprimen —
            // solo metadata del evento (antes acá se logueaba targetUrl
            // completo, que incluía el JWT en texto plano).
            log.info("OAuth2: redirect con código de intercambio de un solo uso emitido");
            getRedirectStrategy().sendRedirect(request, response, targetUrl);

        } catch (Exception e) {
            log.error("Error en OAuth2 authentication success handler", e);
            String errorUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/auth")
                    .queryParam("error", "oauth_failed")
                    .build()
                    .toUriString();
            getRedirectStrategy().sendRedirect(request, response, errorUrl);
        }
    }

    private AppUser createNewOAuth2User(String email, String name, String picture) {
        log.info("Creando nuevo usuario desde OAuth2: {}", email);

        AppUser newUser = new AppUser();
        newUser.setEmail(email);
        newUser.setPasswordHash(""); // No password for OAuth users
        // Mismo rol seguro por defecto que usa el registro tradicional
        // (AuthService.register / AppUser.role) cuando no hay uno válido:
        // Google nunca puede otorgar un rol privilegiado.
        newUser.setRole("scrum_member");
        newUser.setNombre(name);

        return appUserRepository.save(newUser);
    }
}
