// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InvitarProyectoRequest(
    @NotBlank @Email String email,
    /** Opcional. Si se omite, ProjectMemberService.invitar() conserva el
     *  comportamiento previo a V39: la invitación queda como scrum_member. */
    @Pattern(regexp = "scrum_member|product_owner") String rol
) {}
