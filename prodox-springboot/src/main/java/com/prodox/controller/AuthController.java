package com.prodox.controller;

import com.prodox.dto.AuthRequest;
import com.prodox.dto.AuthResponse;
import com.prodox.dto.ForgotPasswordRequest;
import com.prodox.dto.ResetPasswordRequest;
import com.prodox.service.AuthService;
import com.prodox.service.PasswordResetService;
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

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Respuesta siempre genérica, exista o no el correo — evita que este
     * endpoint sirva para enumerar cuentas registradas.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.solicitarRecuperacion(request.email());
        return ResponseEntity.ok(Map.of("message",
                "Si el correo está registrado, recibirás instrucciones para recuperar tu contraseña."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.restablecerContrasena(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente."));
    }
}
