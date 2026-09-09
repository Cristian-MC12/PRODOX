package com.prodox.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.isValid(token)) {
                String userId = jwtUtil.getUserId(token);
                String role   = jwtUtil.getRole(token);
                var auth = new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                // Bloque de seguridad Logging/Exposición (H4, Bloque 8A):
                // evento de seguridad — JWT presente pero inválido/expirado.
                // Nunca se registra el valor del token, solo la ruta
                // solicitada (útil para detectar patrones de abuso). No se
                // altera el comportamiento: la request sigue sin autenticar
                // y el AuthenticationEntryPoint de SecurityConfig decide el
                // 401 más adelante en la cadena, igual que antes.
                log.warn("JWT inválido o expirado en request a {}", request.getRequestURI());
            }
        }
        filterChain.doFilter(request, response);
    }
}
