package com.prodox.controller;

import com.prodox.dto.AuthRequest;
import com.prodox.dto.AuthResponse;
import com.prodox.dto.ForgotPasswordRequest;
import com.prodox.dto.OAuth2ExchangeRequest;
import com.prodox.dto.ResetPasswordRequest;
import com.prodox.ratelimit.AuthRateLimitService;
import com.prodox.security.JwtUtil;
import com.prodox.security.OAuth2ExchangeCodeService;
import com.prodox.service.AuthService;
import com.prodox.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final AuthRateLimitService authRateLimitService;
    private final OAuth2ExchangeCodeService oAuth2ExchangeCodeService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        // Rate limiting ANTES de cualquier lógica de negocio — mismo patrón
        // ya usado en AICopilotController.chat() con RateLimitService.
        authRateLimitService.verificarRegistro(httpRequest.getRemoteAddr());
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        authRateLimitService.verificarLogin(httpRequest.getRemoteAddr(), request.email());
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Respuesta siempre genérica, exista o no el correo — evita que este
     * endpoint sirva para enumerar cuentas registradas. El rate limit se
     * verifica ANTES de consultar la existencia del usuario (dentro de
     * passwordResetService.solicitarRecuperacion) — el contador por email
     * se incrementa exista o no la cuenta, así que ni el 429 ni el 200
     * revelan nada sobre si el correo está registrado.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        authRateLimitService.verificarForgotPassword(httpRequest.getRemoteAddr(), request.email());
        passwordResetService.solicitarRecuperacion(request.email());
        return ResponseEntity.ok(Map.of("message",
                "Si el correo está registrado, recibirás instrucciones para recuperar tu contraseña."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        authRateLimitService.verificarResetPassword(httpRequest.getRemoteAddr());
        passwordResetService.restablecerContrasena(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente."));
    }

    /**
     * Bloque de seguridad JWT/OAuth2: canjea el código opaco de un solo uso
     * recibido en el redirect de OAuth2 (?code=...) por el JWT real de
     * sesión — ver OAuth2AuthenticationSuccessHandler/OAuth2ExchangeCodeService.
     * El JWT solo viaja en el cuerpo JSON de esta respuesta, nunca en la URL.
     *
     * Público (sin JWT todavía en este punto del flujo): el propio código de
     * un solo uso, corto y de vida muy corta, es la credencial que autoriza
     * este canje — no una sesión ya establecida.
     *
     * Error genérico deliberado (código inexistente, ya usado o expirado
     * reciben la MISMA respuesta) para no revelar cuál de los tres casos
     * ocurrió.
     */
    @PostMapping("/oauth2/exchange")
    public ResponseEntity<AuthResponse> exchangeOAuth2Code(@Valid @RequestBody OAuth2ExchangeRequest request) {
        String jwt = oAuth2ExchangeCodeService.canjear(request.code())
                .orElseThrow(() -> new IllegalArgumentException("Código de intercambio inválido o expirado."));
        AuthResponse response = new AuthResponse(
                jwt,
                jwtUtil.getUserId(jwt),
                jwtUtil.getEmail(jwt),
                jwtUtil.getRole(jwt),
                jwtUtil.getNombre(jwt)
        );
        return ResponseEntity.ok(response);
    }
}
