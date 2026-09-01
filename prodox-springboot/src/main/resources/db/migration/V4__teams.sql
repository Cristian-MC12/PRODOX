-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V4 — Sistema de equipos Scrum con código de invitación y email

CREATE TABLE IF NOT EXISTS teams (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre           VARCHAR(255) NOT NULL,
    codigo_invitacion VARCHAR(20) NOT NULL UNIQUE,
    scrum_master_id  VARCHAR(255) NOT NULL,
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Invitaciones pendientes por email
CREATE TABLE IF NOT EXISTS team_invitaciones (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id    UUID        NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    email      VARCHAR(255) NOT NULL,
    token      VARCHAR(64) NOT NULL UNIQUE,
    usado      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Membresía: vincula usuarios (por su UUID como string) a equipos
-- No toca app_users para evitar problemas de permisos
CREATE TABLE IF NOT EXISTS user_team_memberships (
    user_id    VARCHAR(255) NOT NULL,
    team_id    UUID         NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    joined_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, team_id)
);
