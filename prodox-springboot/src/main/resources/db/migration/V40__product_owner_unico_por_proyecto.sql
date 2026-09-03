-- Autor: Cristian Santiago Martinez Cordoba — PRODOX
-- V40 — Garantiza EN LA BASE DE DATOS que un proyecto tenga como máximo un
-- Product Owner activo (ProjectMember.rol = 'product_owner').
--
-- La validación de aplicación (ProjectMemberService.invitar/unirse/cambiarRol)
-- ya rechaza intentar crear un segundo Product Owner, pero esa validación es
-- un SELECT seguido de un INSERT/UPDATE: bajo llamadas concurrentes (o
-- directas al endpoint, sin pasar por la UI) queda una ventana de carrera
-- entre ambas operaciones. Este índice único parcial es el respaldo real a
-- nivel de datos — Postgres rechaza la segunda fila con rol='product_owner'
-- para el mismo proyecto sin importar qué camino de la aplicación la generó.
--
-- Es un índice PARCIAL (WHERE rol = 'product_owner'): no afecta la
-- posibilidad de tener múltiples scrum_member ni al scrum_master existente
-- por proyecto, solo restringe la combinación (proyecto_id, 'product_owner').
CREATE UNIQUE INDEX uq_project_members_un_po_por_proyecto
    ON project_members (proyecto_id)
    WHERE rol = 'product_owner';
