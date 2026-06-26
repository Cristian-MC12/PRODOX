-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V8 — Membresía por proyecto (reemplaza membresía global por equipo)

CREATE TABLE IF NOT EXISTS project_members (
    proyecto_id  UUID         NOT NULL REFERENCES proyectos(id) ON DELETE CASCADE,
    user_id      VARCHAR(255) NOT NULL,
    user_email   VARCHAR(255) NOT NULL,
    rol          VARCHAR(50)  NOT NULL DEFAULT 'scrum_member',
    joined_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (proyecto_id, user_id)
);

-- Invitaciones por proyecto
CREATE TABLE IF NOT EXISTS project_invitaciones (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_id  UUID        NOT NULL REFERENCES proyectos(id) ON DELETE CASCADE,
    email        VARCHAR(255) NOT NULL,
    token        VARCHAR(64) NOT NULL UNIQUE,
    codigo       VARCHAR(20) NOT NULL,
    usado        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
