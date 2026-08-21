-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V25 — Corrige unicidad de metric_parametrizaciones para permitir versionado real (Fase 16.10)
--
-- CONTEXTO:
-- V14 creó un índice único global (user_id, metrica_id) para el flujo de
-- "ranking" (MetricRankingService.guardar()), que asume como máximo UNA
-- parametrización de referencia por usuario y métrica, sin considerar
-- proyecto ni versión.
--
-- V21 introdujo el versionado formal por proyecto (ParametrizacionService:
-- guardarPropuesta()/aprobarParametrizacion()), donde el MISMO usuario debe
-- poder crear v1, v2, v3... sucesivas para la MISMA métrica dentro de un
-- proyecto. V21 nunca ajustó el índice de V14, que bloquea ese flujo desde
-- la segunda versión en adelante (confirmado en producción: intentar
-- guardar v3 sobre Prueba 1 / SIG-SC-02 falló con
-- ConstraintViolationException «ux_parametrizacion_user_metrica»).
--
-- Verificado contra los datos reales antes de escribir esta migración:
-- - 10/10 filas existentes tienen proyecto_id NOT NULL y metrica_id NOT NULL
--   (no existe ninguna fila con proyecto_id NULL en la BD actual).
-- - 0 filas violan la combinación (proyecto_id, metrica_id, version).
-- - 0 duplicados existen hoy por (user_id, metrica_id): el índice de V14
--   nunca tuvo que prevenir un duplicado real en la historia de esta BD.
-- - El único caso con múltiples versiones (Prueba 1 / SIG-SC-02: v1
--   inactiva, v2 aprobada) es compatible sin conflicto con el índice nuevo.
--
-- ux_parametrizacion_user_factor (la gemela de V14 para el flujo por
-- factor_id, sin metrica_id) NO se toca: protege un caso de uso distinto,
-- no relacionado con el bloqueo investigado, y hoy no tiene ninguna fila
-- real que dependa de ella (0 filas con metrica_id NULL en la BD actual).

-- 1. Retirar la restricción heredada que bloquea el versionado.
DROP INDEX IF EXISTS ux_parametrizacion_user_metrica;

-- 2. Garantizar la unicidad real que necesita el flujo de versionado:
--    a lo sumo una fila por (proyecto, métrica, versión).
CREATE UNIQUE INDEX IF NOT EXISTS ux_parametrizacion_proyecto_metrica_version
    ON metric_parametrizaciones (proyecto_id, metrica_id, version)
    WHERE proyecto_id IS NOT NULL AND metrica_id IS NOT NULL;
