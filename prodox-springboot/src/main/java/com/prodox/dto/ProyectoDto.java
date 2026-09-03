// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
    Instant   createdAt,
    /** Rol POR PROYECTO del usuario que pidió este DTO (V39): scrum_master |
     *  product_owner | scrum_member. Nunca el rol global de AppUser — permite
     *  al frontend mostrar/ocultar UI sin comparar por email ni asumir que
     *  "si no es Scrum Master, es Scrum Member" (ya existe un tercer rol). */
    String    miRol,
    /** V41 — timebox real de la iteración: HORAS | DIAS | SEMANAS +
     *  duración en esa unidad. timeBoxSemanas se conserva sin cambios como
     *  campo legado (ver Proyecto.timeBoxSemanas). */
    String    timeboxUnidad,
    int       timeboxDuracion,
    /** Solo no-null cuando timeboxUnidad="HORAS". */
    LocalTime horaInicio
) {}
