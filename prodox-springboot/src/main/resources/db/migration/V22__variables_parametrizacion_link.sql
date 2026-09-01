-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V22 — Vincular Variables con Parametrizaciones aprobadas (Fase 16.7)

-- ============================================================
-- 1. AGREGAR LINK A PARAMETRIZACIONES
-- ============================================================

ALTER TABLE variables
    ADD COLUMN IF NOT EXISTS parametrizacion_id UUID REFERENCES metric_parametrizaciones(id),
    ADD COLUMN IF NOT EXISTS parametrizacion_version INTEGER;

-- ============================================================
-- 2. ÍNDICE PARA BÚSQUEDA RÁPIDA
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_variables_parametrizacion 
    ON variables(parametrizacion_id, parametrizacion_version);

-- ============================================================
-- 3. COMENTARIOS
-- ============================================================

COMMENT ON COLUMN variables.parametrizacion_id IS 'Parametrización aprobada que generó esta variable (Fase 16.7)';
COMMENT ON COLUMN variables.parametrizacion_version IS 'Versión de la parametrización que generó esta variable';

-- ============================================================
-- 4. CONSTRAINT PARA EVITAR DUPLICADOS
-- ============================================================

-- Una variable por parametrización/versión/proyecto/métrica
CREATE UNIQUE INDEX IF NOT EXISTS idx_variables_unique_parametrizacion
    ON variables(parametrizacion_id, parametrizacion_version, proyecto_id, metrica_id)
    WHERE parametrizacion_id IS NOT NULL;
