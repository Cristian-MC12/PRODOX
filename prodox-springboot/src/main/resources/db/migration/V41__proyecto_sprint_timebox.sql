-- Autor: Cristian Santiago Martinez Cordoba — PRODOX
-- V41 — Timebox configurable de la iteración/Sprint: además de semanas
-- (única unidad soportada hasta ahora vía time_box_semanas), un proyecto
-- ahora puede expresar el timebox en HORAS, DIAS o SEMANAS.
--
-- time_box_semanas NO se elimina ni cambia de significado: sigue siendo el
-- campo legado que ya leen AICopilotService, CopilotToolsService y el email
-- de invitación de ProjectMemberService (fuera del alcance de este cambio;
-- ver ProyectoService.equivalenteEnSemanas). Para proyectos nuevos con
-- timebox_unidad != 'SEMANAS' se lo sigue completando con un equivalente
-- aproximado en semanas (redondeado hacia arriba, acotado a 1-4 para no
-- violar el CHECK de V5) — es solo una aproximación de compatibilidad,
-- NUNCA la fuente de verdad del cálculo real de fechas de sprint, que vive
-- en timebox_unidad + timebox_duracion (y, para HORAS, hora_inicio +
-- sprints.fecha_hora_inicio/fecha_hora_fin).

ALTER TABLE proyectos
    ADD COLUMN timebox_unidad VARCHAR(10) NOT NULL DEFAULT 'SEMANAS';

ALTER TABLE proyectos
    ADD CONSTRAINT chk_proyectos_timebox_unidad CHECK (timebox_unidad IN ('HORAS', 'DIAS', 'SEMANAS'));

ALTER TABLE proyectos
    ADD COLUMN timebox_duracion INTEGER;

-- Backfill: todo proyecto existente ya expresa su timebox en semanas —
-- timebox_duracion queda exactamente igual a time_box_semanas, sin pérdida
-- ni cambio de comportamiento para proyectos ya creados.
UPDATE proyectos SET timebox_duracion = time_box_semanas WHERE timebox_duracion IS NULL;

ALTER TABLE proyectos
    ALTER COLUMN timebox_duracion SET NOT NULL;

ALTER TABLE proyectos
    ADD CONSTRAINT chk_proyectos_timebox_duracion CHECK (timebox_duracion > 0);

-- Hora de inicio del primer sprint — solo tiene sentido y se exige (a nivel
-- de aplicación, ver ProyectoService) cuando timebox_unidad = 'HORAS';
-- nullable para días/semanas y para todos los proyectos existentes.
ALTER TABLE proyectos
    ADD COLUMN hora_inicio TIME;

-- Representación temporal real (fecha Y hora) del inicio/fin de un sprint,
-- necesaria para expresar un timebox en HORAS sin perder precisión ni
-- limitarse artificialmente a las 23:59 del mismo día (un timebox de 10
-- horas iniciado a las 16:00 debe poder terminar a las 02:00 del día
-- siguiente). Nullable: los sprints existentes (todos calculados en
-- semanas) siguen usando exclusivamente fecha_inicio/fecha_fin (DATE), sin
-- ningún cambio — estas columnas nuevas solo se completan para sprints
-- nuevos cuyo proyecto tenga el timebox en horas.
ALTER TABLE sprints
    ADD COLUMN fecha_hora_inicio TIMESTAMPTZ;

ALTER TABLE sprints
    ADD COLUMN fecha_hora_fin TIMESTAMPTZ;
