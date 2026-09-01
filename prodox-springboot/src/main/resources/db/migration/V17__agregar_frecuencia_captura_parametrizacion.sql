-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V17 — Agregar frecuencia_captura a metric_parametrizaciones

ALTER TABLE metric_parametrizaciones
    ADD COLUMN IF NOT EXISTS frecuencia_captura VARCHAR(20) DEFAULT 'por_sprint';

COMMENT ON COLUMN metric_parametrizaciones.frecuencia_captura IS 
    'Frecuencia recomendada por IA para captura de datos: por_sprint | semanal | diaria | ilimitada';
