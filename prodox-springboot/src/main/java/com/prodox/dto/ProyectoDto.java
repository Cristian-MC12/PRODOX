// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProyectoDto(
    UUID      id,
    String    nombre,
    String    descripcion,
    String    metodo,
    int       timeBoxSemanas,
    int       numeroSprints,
    LocalDate fechaInicio,
    String    productGoal,
    String    sprintGoal,
    String    estado,
    String    scrumMasterEmail,
    int       totalMiembros,
    Instant   createdAt
) {}
