-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V9 — Sistema completo: métricas del profesor, variables, ejecución y evaluación

-- ============================================================
-- 1. ACTUALIZAR proyectos: agregar numero_sprints y fecha_inicio
-- ============================================================
ALTER TABLE proyectos
    ADD COLUMN IF NOT EXISTS numero_sprints   INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS fecha_inicio     DATE    NO