-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- Revisión de captura por parametrización: el alcance/responsable de captura
-- (EQUIPO | SCRUM_MASTER) pasa a ser una decisión explícita del Scrum Master
-- al proponer/aprobar cada parametrización, en vez de quedar fijo en "grupal"
-- para todas las variables (ver VariableDinamicaService/ParametrizacionService
-- .crearVariablesDesdeParametrizacion(), que antes hacían
-- variable.setTipoAlcance("grupal") incondicionalmente).
--
-- Todas las parametrizaciones EXISTENTES se backfillean explícitamente a
-- 'SCRUM_MASTER' — no hay evidencia en los datos actuales de cuáles deberían
-- representar EQUIPO, y sus Variable.tipoAlcance ya materializadas son
-- 'grupal' (equivalente a SCRUM_MASTER), así que esto conserva el
-- comportamiento actual exactamente como estaba, sin convertir nada
-- silenciosamente a EQUIPO.
ALTER TABLE metric_parametrizaciones
    ADD COLUMN responsable_captura VARCHAR(20);

UPDATE metric_parametrizaciones
    SET responsable_captura = 'SCRUM_MASTER'
    WHERE responsable_captura IS NULL;

ALTER TABLE metric_parametrizaciones
    ALTER COLUMN responsable_captura SET DEFAULT 'SCRUM_MASTER';

ALTER TABLE metric_parametrizaciones
    ALTER COLUMN responsable_captura SET NOT NULL;

ALTER TABLE metric_parametrizaciones
    ADD CONSTRAINT chk_responsable_captura CHECK (responsable_captura IN ('EQUIPO', 'SCRUM_MASTER'));
