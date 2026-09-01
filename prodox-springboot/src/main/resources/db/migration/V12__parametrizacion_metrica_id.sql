-- V12 — Agrega metrica_id a metric_parametrizaciones para vincular con la tabla metricas
ALTER TABLE metric_parametrizaciones
    ADD COLUMN IF NOT EXISTS metrica_id UUID REFERENCES metricas(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_mp_metrica_id ON metric_parametrizaciones(metrica_id);
