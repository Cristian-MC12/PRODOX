-- Autor: Cristian Santiago Martinez Cordoba — PRODOX
-- V51 — Evaluación manual del cumplimiento del objetivo del sprint (FASE 6B)

-- Tabla para almacenar la evaluación manual del objetivo de un sprint.
-- Relación 1:1 con sprints (un sprint tiene como máximo una evaluación).
-- Solo el Scrum Master puede crear/modificar evaluaciones.
CREATE TABLE IF NOT EXISTS evaluaciones_objetivo (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    sprint_id         UUID        NOT NULL UNIQUE REFERENCES sprints(id) ON DELETE CASCADE,
    estado            VARCHAR(30) NOT NULL CHECK (estado IN ('cumplido', 'parcialmente_cumplido', 'no_cumplido', 'no_evaluable')),
    justificacion     VARCHAR(2000) NOT NULL,
    evidencia_urls    JSONB,
    evaluado_por      VARCHAR(255) NOT NULL,
    evaluado_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_por   VARCHAR(255),
    actualizado_at    TIMESTAMPTZ
);

-- Índices para consultas frecuentes
CREATE INDEX idx_evaluaciones_objetivo_sprint ON evaluaciones_objetivo(sprint_id);
CREATE INDEX idx_evaluaciones_objetivo_estado ON evaluaciones_objetivo(estado);
CREATE INDEX idx_evaluaciones_objetivo_evaluado_por ON evaluaciones_objetivo(evaluado_por);

-- Comentarios para documentación
COMMENT ON TABLE evaluaciones_objetivo IS 
    'Evaluación manual del cumplimiento del objetivo del sprint. FASE 6B: evaluación manual sin criterios cuantitativos automáticos.';

COMMENT ON COLUMN evaluaciones_objetivo.estado IS 
    'Estado de cumplimiento: cumplido (alcanzado), parcialmente_cumplido (progreso significativo), no_cumplido (no alcanzado, no implica sprint fallido), no_evaluable (información insuficiente).';

COMMENT ON COLUMN evaluaciones_objetivo.justificacion IS 
    'Justificación obligatoria del Scrum Master sobre la evaluación. Máximo 2000 caracteres.';

COMMENT ON COLUMN evaluaciones_objetivo.evidencia_urls IS 
    'URLs opcionales de evidencia (demos, documentos, artefactos). Formato JSON array de strings.';

COMMENT ON COLUMN evaluaciones_objetivo.evaluado_por IS 
    'Usuario (Scrum Master) que creó la evaluación. Se obtiene del principal autenticado.';

COMMENT ON COLUMN evaluaciones_objetivo.actualizado_por IS 
    'Usuario que modificó la evaluación por última vez. Null si nunca se modificó.';

COMMENT ON COLUMN evaluaciones_objetivo.actualizado_at IS 
    'Fecha y hora de última modificación. Null si nunca se modificó.';
