-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V14 — Ranking global: una parametrización por (user_id, metrica_id).
--        Limpiar duplicados existentes y agregar índices únicos.

-- 1. Limpiar duplicados con metrica_id (dejar solo la más reciente de cada usuario por métrica)
DELETE FROM metric_parametrizaciones
WHERE id NOT IN (
    SELECT DISTINCT ON (user_id, metrica_id) id
    FROM metric_parametrizaciones
    WHERE metrica_id IS NOT NULL
    ORDER BY user_id, metrica_id, created_at DESC
);

-- 2. Limpiar duplicados con factor_id (sin metrica_id)
DELETE FROM metric_parametrizaciones
WHERE metrica_id IS NULL
  AND factor_id IS NOT NULL
  AND id NOT IN (
    SELECT DISTINCT ON (user_id, factor_id) id
    FROM metric_parametrizaciones
    WHERE metrica_id IS NULL AND factor_id IS NOT NULL
    ORDER BY user_id, factor_id, created_at DESC
);

-- 3. Índice único global: un usuario solo tiene una parametrización por métrica
CREATE UNIQUE INDEX IF NOT EXISTS ux_parametrizacion_user_metrica
    ON metric_parametrizaciones (user_id, metrica_id)
    WHERE metrica_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_parametrizacion_user_factor
    ON metric_parametrizaciones (user_id, factor_id)
    WHERE metrica_id IS NULL AND factor_id IS NOT NULL;
