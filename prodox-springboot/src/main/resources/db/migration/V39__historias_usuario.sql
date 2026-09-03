-- Autor: Cristian Santiago Martinez Cordoba — PRODOX
-- V39 — Rol PRODUCT_OWNER (por proyecto) + backlog de historias de usuario.
--
-- 1) project_members.rol ya es VARCHAR(50) libre (V8): "product_owner" se
--    admite como tercer valor sin ALTER de esquema, solo a nivel de
--    aplicación (ProjectMemberService/HistoriaUsuarioController). No se
--    toca la columna ni sus datos existentes.
--
-- 2) project_invitaciones necesita poder recordar CON QUÉ ROL fue invitado
--    un correo (hoy toda invitación aceptada crea scrum_member a fuego,
--    ver ProjectMemberService.unirse() antes de esta migración). Se agrega
--    'rol' con DEFAULT 'scrum_member' para que las invitaciones ya
--    existentes (creadas antes de esta migración, todavía no usadas) sigan
--    resolviendo exactamente igual que antes: scrum_member.
ALTER TABLE project_invitaciones
    ADD COLUMN rol VARCHAR(20) NOT NULL DEFAULT 'scrum_member';

ALTER TABLE project_invitaciones
    ADD CONSTRAINT chk_project_invitaciones_rol CHECK (rol IN ('scrum_member', 'product_owner'));

-- 3) Backlog de historias de usuario. sprint_id nullable: una historia puede
--    vivir en el backlog sin estar asignada a ningún sprint todavía
--    (ON DELETE SET NULL si el sprint se elimina, para no perder la
--    historia). proyecto_id con CASCADE, igual que sprints/project_members.
CREATE TABLE historias_usuario (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_id           UUID         NOT NULL REFERENCES proyectos(id) ON DELETE CASCADE,
    sprint_id             UUID         REFERENCES sprints(id) ON DELETE SET NULL,
    titulo                VARCHAR(200) NOT NULL,
    descripcion           TEXT,
    criterios_aceptacion  TEXT,
    prioridad             VARCHAR(10)  NOT NULL DEFAULT 'media'     CHECK (prioridad IN ('alta', 'media', 'baja')),
    estado                VARCHAR(20)  NOT NULL DEFAULT 'pendiente' CHECK (estado IN ('pendiente', 'en_progreso', 'completada')),
    creado_por            VARCHAR(255) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_historias_usuario_proyecto ON historias_usuario(proyecto_id);
CREATE INDEX idx_historias_usuario_sprint   ON historias_usuario(sprint_id);
