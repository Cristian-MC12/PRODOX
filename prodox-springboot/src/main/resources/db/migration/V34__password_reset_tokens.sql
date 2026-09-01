-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V34 — Tokens de recuperación de contraseña.
-- Tabla nueva (no toca app_users), igual que project_invitaciones en V8:
-- mpdia_user la crea y es su dueño, sin problema de permisos.

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token      VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    usado      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
