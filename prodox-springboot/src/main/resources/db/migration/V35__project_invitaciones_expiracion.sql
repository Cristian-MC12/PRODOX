-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V35 — Expiración para invitaciones a proyecto (project_invitaciones ya
-- soporta uso único vía "usado"; faltaba expiración). Nullable: las
-- invitaciones creadas antes de esta migración no la tienen y se tratan
-- como no-expirables (ver ProjectMemberService).
ALTER TABLE project_invitaciones ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
