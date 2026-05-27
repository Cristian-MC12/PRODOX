package com.mpdia.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    String role   // opcional en login, requerido en registro: "scrum_member" | "scrum_master"
) {}
