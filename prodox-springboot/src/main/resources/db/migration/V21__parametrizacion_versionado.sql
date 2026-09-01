-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V21 — Versionado y aprobación formal de parametrizaciones

-- Agregar versionado y snapshots
ALTER TABLE metric_parametrizaciones
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS propuesta_ia_json JSONB,
    ADD COLUMN IF NOT EXISTS configuracion_aprobada_json JSONB;

-- Actualizar estados existentes para claridad semántica
-- 'pendiente' → 'propuesta' (generada/editada, no aprobada formalmente)
UPDATE metric_parametrizaciones
SET status = 'propuesta'
WHERE status = 'pendiente';

-- Comentarios para documentación
COMMENT ON COLUMN metric_parametrizaciones.version IS 
    'Versión de la parametrización. v1 = inicial, v2+ = modificaciones posteriores.';

COMMENT ON COLUMN metric_parametrizaciones.propuesta_ia_json IS 
    'Propuesta original generada por Gemini (snapshot inmutable para auditoría).';

COMMENT ON COLUMN metric_parametrizaciones.configuracion_aprobada_json IS 
    'Configuración exacta aprobada por el usuario (snapshot para reproducibilidad de cálculos).';

COMMENT ON COLUMN metric_parametrizaciones.status IS 
    'Estado: propuesta (no aprobada) | aprobada (lista para uso) | rechazada | inactiva (reemplazada por nueva versión).';

-- Índices para performance en queries por versión
CREATE INDEX IF NOT EXISTS idx_metric_param_metrica_version 
    ON metric_parametrizaciones(metrica_id, version DESC)
    WHERE metrica_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_metric_param_status_version 
    ON metric_parametrizaciones(status, version DESC);

CREATE INDEX IF NOT EXISTS idx_metric_param_proyecto_metrica_aprobada
    ON metric_parametrizaciones(proyecto_id, metrica_id, status)
    WHERE status = 'aprobada';
