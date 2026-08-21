-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V26 — Corrige unicidad de variables para permitir versionado real (Fase 16.10)
--
-- CONTEXTO:
-- V10 creó variables_proyecto_id_metrica_id_key: UNIQUE(proyecto_id, metrica_id),
-- pensado únicamente para el flujo de Planeación (PlaneacionService), que asume
-- como máximo UNA variable por proyecto y métrica, sin considerar parametrización
-- ni versión.
--
-- V22 introdujo parametrizacion_id/parametrizacion_version en variables para el
-- flujo formal de parametrización por proyecto (ParametrizacionService.
-- crearVariablesDesdeParametrizacion()), donde cada nueva versión aprobada (v1,
-- v2, v3...) de la MISMA métrica en el MISMO proyecto debe poder tener sus
-- propias variables. V22 nunca ajustó el índice de V10, que bloquea ese flujo
-- desde la segunda versión en adelante (confirmado en producción: aprobar v3
-- sobre Prueba 1 / SIG-SC-02 falló con ConstraintViolationException
-- «variables_proyecto_id_metrica_id_key»).
--
-- Además, el índice único que V22 sí creó (idx_variables_unique_parametrizacion)
-- no incluye "nombre" en su clave, lo que bloquearía a futuro una parametrización
-- con más de una variable en la misma versión (no manifestado aún en datos
-- reales, pero es un defecto latente que esta migración corrige de una vez).
--
-- Verificado contra los datos reales antes de escribir esta migración:
-- - 0 filas violan (parametrizacion_id, parametrizacion_version, nombre).
-- - 0 filas violan (proyecto_id, metrica_id) entre las filas de Planeación
--   (parametrizacion_id IS NULL).
-- - 0 filas con parametrizacion_id NOT NULL tienen parametrizacion_version o
--   nombre NULL.
-- - parametrizacion_id IS NULL / IS NOT NULL es un discriminador limpio y
--   verificado en código entre Sistema A (PlaneacionService, siempre usa
--   proyecto_id + metrica_id) y Sistema B (VariableDinamicaService,
--   ParametrizacionService, MetricaAcademicaService, CalculoMetricaService,
--   siempre usan parametrizacion_id + parametrizacion_version).

-- 1. Retirar la restricción heredada que bloquea el versionado de Sistema B.
ALTER TABLE variables
    DROP CONSTRAINT IF EXISTS variables_proyecto_id_metrica_id_key;

-- 2. Restaurar la unicidad real que necesita Sistema A (Planeación): a lo sumo
--    una variable "de catálogo" por proyecto y métrica, fuera de parametrización.
CREATE UNIQUE INDEX IF NOT EXISTS ux_variables_proyecto_metrica_planeacion
    ON variables (proyecto_id, metrica_id)
    WHERE parametrizacion_id IS NULL;

-- 3. Sustituir el índice de V22, que no protege contra nombres duplicados
--    dentro de una misma versión.
DROP INDEX IF EXISTS idx_variables_unique_parametrizacion;

-- 4. Unicidad real de Sistema B: a lo sumo una fila por (parametrización,
--    versión, nombre), permitiendo múltiples variables por versión y
--    versiones sucesivas independientes entre sí.
CREATE UNIQUE INDEX IF NOT EXISTS ux_variables_parametrizacion_version_nombre
    ON variables (
        parametrizacion_id,
        parametrizacion_version,
        nombre
    )
    WHERE parametrizacion_id IS NOT NULL;
