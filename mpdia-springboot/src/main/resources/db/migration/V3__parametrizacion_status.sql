-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V3 — Estado de verificación en parametrizaciones

ALTER TABLE metric_parametrizaciones
    ADD COLUMN IF NOT EXISTS status        VARCHAR(30)  NOT NULL DEFAULT 'pendiente',
    ADD COLUMN IF NOT EXISTS revisado_por  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS revisado_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS motivo_rechazo TEXT;

-- status: pendiente | aprobada | rechazada
