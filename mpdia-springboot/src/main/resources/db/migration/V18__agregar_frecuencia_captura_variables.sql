-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V18 — Agregar frecuencia_captura a variables

ALTER TABLE variables
    ADD COLUMN IF NOT EXISTS frecuencia_captura VARCHAR(20) DEFAULT 'por_sprint';

COMMENT ON COLUMN variables.frecuencia_captura IS 
    'Frecuencia de captura recomendada por IA (copiada desde parametrización): por_sprint | semanal | diaria | ilimitada';
