package com.mpdia.dto;

public record AuthResponse(
    String token,
    String userId,
    String email,
    String role
) {}
