package com.prodox.dto;

public record AuthResponse(
    String token,
    String userId,
    String email,
    String role,
    String nombre
) {}
