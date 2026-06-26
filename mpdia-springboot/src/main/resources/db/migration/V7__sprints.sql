-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V7 — Sprints múltiples por proyecto

CREATE TABLE IF NOT EXISTS sprints (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_id  UUID        NOT NULL REFERENCES proyectos(id) ON DELETE CASCADE,
    numero       INTEGER     NOT NULL,
    sprint_goal  TEXT        NOT NULL,
    estado       VARCHAR(20) NOT NULL DEFAULT 'activo' CHECK (estado IN ('activo', 'finalizado')),
    fecha_inicio DATE        NOT NULL DEFAULT CURRENT_DATE,
    fecha_fin    DATE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (proyecto_id, numero)
);

-- Migrar el sprint_goal del proyecto al primer sprint
-- (los proyectos existentes quedan con sus datos, el trigger crea el sprint)
