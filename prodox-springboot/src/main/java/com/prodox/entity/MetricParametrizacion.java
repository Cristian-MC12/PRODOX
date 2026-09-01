// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Parametrización de una métrica creada por un usuario.
 * Es INMUTABLE: nunca se actualiza, solo se inserta.
 * 
 * VERSIONADO (Fase 16.6):
 * - version: número de versión (1, 2, 3...)
 * - v1 = parametrización inicial aprobada
 * - v2+ = modificaciones posteriores
 * - Una vez aprobada una versión, se marca como INACTIVA si se crea una nueva
 * 
 * SNAPSHOT:
 * - propuestaIAJson: propuesta original de Gemini (auditoría)
 * - configuracionAprobadaJson: configuración exacta aprobada (reproducibilidad)
 * 
 * ESTADOS:
 * - propuesta: generada/editada, no aprobada
 * - aprobada: configuración validada y lista para uso en cálculos
 * - rechazada: rechazada por el usuario
 * - inactiva: reemplazada por nueva versión
 */
@Entity
@Table(name = "metric_parametrizaciones")
@Data
@NoArgsConstructor
public class MetricParametrizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /** Versión de esta parametrización (1, 2, 3...) */
    @Column(nullable = false)
    private Integer version = 1;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "factor_id", nullable = true)
    private Factor factor;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String objetivo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String procedimiento;

    @Column(name = "indicador_variable", nullable = false, length = 500)
    private String indicadorVariable;

    @Column(nullable = false, length = 255)
    private String escala;

    /**
     * Escala estructurada (reemplaza depender de parsear `escala` en texto libre).
     * escalaTipo NULL = parametrización histórica sin estructura — no se infiere,
     * se deja explícitamente sin restricción (ver migración V32).
     */
    @Column(name = "escala_tipo", length = 30)
    private String escalaTipo;

    @Column(name = "escala_min")
    private java.math.BigDecimal escalaMin;

    @Column(name = "escala_max")
    private java.math.BigDecimal escalaMax;

    @Column(name = "escala_paso")
    private java.math.BigDecimal escalaPaso;

    @Column(name = "escala_sin_limite")
    private Boolean escalaSinLimite;

    @Column(name = "escala_descripcion", columnDefinition = "TEXT")
    private String escalaDescripcion;

    /** Si esta parametrización fue copiada de otra, apunta a la original */
    @Column(name = "metrica_base_id")
    private UUID metricaBaseId;

    /** Métrica del catálogo asociada (para búsqueda desde el flujo de Planeación) */
    @Column(name = "metrica_id")
    private UUID metricaId;

    @Column(nullable = false, length = 30)
    private String status = "propuesta"; // propuesta | aprobada | rechazada | inactiva

    @Column(name = "revisado_por")
    private String revisadoPor;

    @Column(name = "revisado_at")
    private Instant revisadoAt;

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    @Column(name = "proyecto_id")
    private UUID proyectoId;
    
    /** Propuesta original generada por Gemini (inmutable, para auditoría) */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "propuesta_ia_json", columnDefinition = "jsonb")
    private String propuestaIAJson;
    
    /** Configuración exacta aprobada por el usuario (snapshot para reproducibilidad) */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "configuracion_aprobada_json", columnDefinition = "jsonb")
    private String configuracionAprobadaJson;
    
    /** Frecuencia de captura recomendada */
    @Column(name = "frecuencia_captura", length = 20)
    private String frecuenciaCaptura = "por_sprint";
    
    /** Fuente académica completa (Fase 16.9.1) */
    @Column(name = "fuente_academica", columnDefinition = "TEXT")
    private String fuenteAcademica;
    
    /** Fórmula matemática según fuente académica (Fase 16.9.1) */
    @Column(name = "formula_academica", length = 500)
    private String formulaAcademica;
    
    /** Tipo de operación: SUMA | PROMEDIO | DIRECTO | FORMULA (Fase 16.9.1) */
    @Column(name = "tipo_operacion", length = 20)
    private String tipoOperacion;

    /**
     * Revisión de captura por parametrización: alcance/responsable de captura
     * definido explícitamente por el Scrum Master — EQUIPO (cada integrante
     * registra su propio valor) o SCRUM_MASTER (solo el Scrum Master
     * registra). Independiente de tipoOperacion: uno decide QUIÉN captura, el
     * otro decide CÓMO se calcula el resultado. Se materializa en
     * Variable.tipoAlcance al aprobar/materializar (ver
     * ParametrizacionService/VariableDinamicaService.crearVariablesDesdeParametrizacion()):
     * EQUIPO → 'individual', SCRUM_MASTER → 'grupal'.
     */
    @Column(name = "responsable_captura", length = 20, nullable = false)
    private String responsableCaptura = "SCRUM_MASTER";
    
    /** Unidad del resultado: problemas, puntos, %, días (Fase 16.9.1) */
    @Column(name = "unidad_resultado", length = 50)
    private String unidadResultado;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * Identificador técnico snake_case de la variable principal (Fase 16.10-E).
     * NO se persiste como columna: indicadorVariable es prosa humana y no es
     * una fuente confiable para derivar un nombre técnico (ver
     * docs/FASE16_10_... investigación de SIG-VEL-02). Se transporta en la
     * respuesta de guardar-propuesta para que el frontend lo conserve como
     * fuente de verdad al aprobar, y queda documentado de forma durable en
     * propuestaIAJson/configuracionAprobadaJson (JSON ya persistido).
     */
    @jakarta.persistence.Transient
    private String nombreVariable;
}
