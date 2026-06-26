-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V6 — Asociar parametrizaciones con proyectos

ALTER TABLE metric_parametrizaciones 
    ADD COLUMN IF NOT EXISTS proyecto_id UUID REFERENCES proyectos(id) ON DELETE SET NULL;
