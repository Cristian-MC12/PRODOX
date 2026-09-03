// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

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
    Instant   createdAt,
    /** V41 — timebox real del proyecto dueño de este sprint: HORAS | DIAS |
     *  SEMANAS + duración en esa unidad (copiado de Proyecto, mismo patrón
     *  que timeBoxSemanas). */
    String    timeboxUnidad,
    Integer   timeboxDuracion,
    /** Representación temporal real (fecha+hora) del inicio/fin de este
     *  sprint — solo no-null cuando el timebox del proyecto está en HORAS.
     *  Para días/semanas siguen siendo la fuente de verdad fechaInicio/fechaFin. */
    Instant   fechaHoraInicio,
    Instant   fechaHoraFin
) {}
