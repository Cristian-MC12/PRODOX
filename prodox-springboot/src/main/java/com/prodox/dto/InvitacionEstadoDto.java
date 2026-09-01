// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

/**
 * Estado público de una invitación, consultable sin autenticación (para que
 * el enlace del correo pueda mostrar "invitación válida/expirada/usada"
 * antes incluso de que el usuario inicie sesión).
 *
 * estado ∈ "valida" | "no_existe" | "expirada" | "usada"
 */
public record InvitacionEstadoDto(
    String proyectoId,
    String proyectoNombre,
    String estado
) {}
