// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.util.UUID;

/** sprintId en null desasigna la historia del sprint (vuelve al backlog). */
public record AsignarSprintHistoriaRequest(
    UUID sprintId
) {}
