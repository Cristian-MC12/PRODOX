// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;

public record CambiarRolMiembroRequest(
    /** Validado en el servicio contra los valores asignables por este endpoint
     *  (scrum_member | product_owner) — nunca scrum_master, ver ProjectMemberService.cambiarRol. */
    @NotBlank String rol
) {}
