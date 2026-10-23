// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Evaluación manual del cumplimiento del objetivo de un sprint.
 * 
 * FASE 6B: Implementación de evaluación manual sin criterios cuantitativos automáticos.
 * El Scrum Master evalúa si el objetivo se cumplió, proporcionando justificación
 * y evidencia opcional. La evaluación es independiente de métricas automáticas.
 * 
 * Estados permitidos:
 * - cumplido: objetivo alcanzado completamente
 * - parcialmente_cumplido: objetivo alcanzado en parte, progreso significativo
 * - no_cumplido: objetivo no alcanzado (no implica sprint fallido)
 * - no_evaluable: información insuficiente o contexto cambió drásticamente
 * 
 * Restricciones:
 * - Máximo una evaluación por sprint (unicidad por sprint_id)
 * - Solo se puede evaluar sprint finalizado o reabierto
 * - Justificación es obligatoria
 * - Usuario evaluador se registra del principal autenticado
 * - Modificaciones conservan creador original y registran modificador
 */
@Entity
@Table(name = "evaluaciones_objetivo")
@Getter @Setter @NoArgsConstructor
public class EvaluacionObjetivo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Sprint al que pertenece esta evaluación.
     * Relación 1:1 (un sprint tiene como máximo una evaluación).
     */
    @Column(name = "sprint_id", nullable = false, unique = true)
    private UUID sprintId;

    /**
     * Estado de cumplimiento del objetivo.
     * Valores: cumplido | parcialmente_cumplido | no_cumplido | no_evaluable
     */
    @Column(nullable = false, length = 30)
    private String estado;

    /**
     * Justificación del Scrum Master.
     * Obligatoria, máximo 2000 caracteres.
     */
    @Column(nullable = false, length = 2000)
    private String justificacion;

    /**
     * URLs de evidencia opcional (demos, documentos, artefactos).
     * Formato JSON: ["https://...", "https://..."]
     * No se valida contenido, solo formato de URL básico en servicio.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidencia_urls", columnDefinition = "jsonb")
    private String evidenciaUrls;

    /**
     * Usuario que creó la evaluación (Scrum Master).
     * Se obtiene del principal autenticado, no del cliente.
     */
    @Column(name = "evaluado_por", nullable = false, length = 255)
    private String evaluadoPor;

    /**
     * Fecha y hora de creación de la evaluación.
     */
    @Column(name = "evaluado_at", nullable = false, updatable = false)
    private Instant evaluadoAt = Instant.now();

    /**
     * Usuario que modificó la evaluación por última vez.
     * Null si nunca se modificó.
     */
    @Column(name = "actualizado_por", length = 255)
    private String actualizadoPor;

    /**
     * Fecha y hora de última modificación.
     * Null si nunca se modificó.
     */
    @Column(name = "actualizado_at")
    private Instant actualizadoAt;
}
