-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V5 — Sistema de proyectos con planificación ágil

CREATE TABLE IF NOT EXISTS proyectos (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre          VARCHAR(255) NOT NULL,
    descripcion     TEXT,
    metodo          VARCHAR(10)  NOT NULL CHECK (metodo IN ('scrum', 'xp')),
    time_box_semanas INTEGER     NOT NULL CHECK (time_box_semanas BETWEEN 1 AND 4),
    product_goal    TEXT         NOT NULL,
    sprint_goal     TEXT         NOT NULL,
    estado          VARCHAR(20)  NOT NULL DEFAULT 'activo' CHECK (estado IN ('activo', 'finalizado')),
    scrum_master_id VARCHAR(255) NOT NULL,
    team_id         UUID         REFERENCES teams(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
