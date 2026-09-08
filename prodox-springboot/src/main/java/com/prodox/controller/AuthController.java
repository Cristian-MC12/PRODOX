package com.prodox.controller;

import com.prodox.dto.AuthRequest;
import com.prodox.dto.AuthResponse;
import com.prodox.dto.ForgotPasswordRequest;
import com.prodox.dto.ResetPasswordRequest;
import com.prodox.ratelimit.AuthRateLimitService;
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
}
