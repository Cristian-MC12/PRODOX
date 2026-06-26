// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SprintDto(
    UUID      id,
    UUID      proyectoId,
    String    proyectoNombre,
    String    metodo,
    Integer   timeBoxSemanas,
    Integer   numero,
    String    sprintGoal,
    /** pendiente | en_ejecucion | finalizado | reabierto */
    String    estado,
    LocalDate fechaInicio,
    LocalDate fechaFin,
    String    cerradoPor,
    Instant   cerradoAt,
    Instant   createdAt
) {}
