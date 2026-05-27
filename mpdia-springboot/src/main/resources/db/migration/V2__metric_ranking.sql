-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- ============================================================
-- V2 — Sistema de ranking y parametrizaciones compartidas
-- ============================================================

-- Parametrizaciones guardadas por usuarios (inmutables por diseño)
-- Cada fila es una versión de parametrización creada por un usuario.
-- metrica_base_id: si esta parametrización fue copiada de otra, apunta a la original.
CREATE TABLE IF NOT EXISTS metric_parametrizaciones (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    factor_id         UUID        NOT NULL REFERENCES factors(id) ON DELETE CASCADE,
    user_id           VARCHAR(255) NOT NULL,
    user_email        VARCHAR(255) NOT NULL,
    objetivo          TEXT        NOT NULL,
    procedimiento     TEXT        NOT NULL,
    indicador_variable VARCHAR(500) NOT NULL,
    escala            VARCHAR(255) NOT NULL,
    metrica_base_id   UUID        REFERENCES metric_parametrizaciones(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Ranking: contador de cuántas veces fue usada cada parametrización base
-- Solo se incrementa cuando alguien selecciona una métrica que ya tiene parametrización base.
CREATE TABLE IF NOT EXISTS metric_uso_ranking (
    factor_id         UUID        PRIMARY KEY REFERENCES factors(id) ON DELETE CASCADE,
    parametrizacion_id UUID       REFERENCES metric_parametrizaciones(id) ON DELETE SET NULL,
    usos              INTEGER     NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
