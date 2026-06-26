-- V13 — Hace factor_id nullable en metric_parametrizaciones para soportar
--        el flujo desde Planeación (donde solo existe metricaId, no factorId)
ALTER TABLE metric_parametrizaciones
    ALTER COLUMN factor_id DROP NOT NULL;
