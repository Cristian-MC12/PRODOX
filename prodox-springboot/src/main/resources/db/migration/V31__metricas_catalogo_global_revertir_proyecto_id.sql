-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V31 — Revierte V30: Metrica vuelve a ser EXCLUSIVAMENTE el catálogo
--        global de definiciones (Fase PRODOX AI — corrección definitiva del
--        modelo de métricas globales).
--
-- CONTEXTO:
-- V30 agregó Metrica.proyecto_id asumiendo que una métrica creada por IA es
-- propiedad privada y permanente del proyecto que la creó. Se confirmó con
-- el usuario que el modelo funcional real es otro: Metrica es el catálogo
-- GLOBAL (como ya era el diseño original de las ~40 métricas semilla,
-- V11__metricas_profesor_correccion.sql); la relación proyecto↔métrica vive
-- en ProyectoMetrica, no en Metrica. Una métrica no tiene "dueño": cualquier
-- proyecto puede reutilizarla vía ProyectoMetrica, y sus parametrizaciones
-- son independientes por proyecto (MetricParametrizacion, único por
-- proyecto_id+metrica_id+version, V25).
--
-- Verificado contra los datos reales antes de escribir esta migración
-- (24/08/2026): 41 métricas en el catálogo, TODAS con proyecto_id NULL —
-- ninguna fila usa la columna que V30 agregó (no hubo tiempo de que el flujo
-- de creación por IA la poblara con datos reales antes de esta corrección).
-- Eliminar la columna no pierde ningún dato.

-- 1. Retirar el índice parcial de V30 (dependía de proyecto_id).
DROP INDEX IF EXISTS ux_metricas_proyecto_nombre;

-- 2. Retirar proyecto_id y su FK: Metrica no tiene proyecto propietario.
ALTER TABLE metricas DROP COLUMN IF EXISTS proyecto_id;

-- 3. Restaurar la unicidad GLOBAL de nombre normalizado (mismo objetivo que
--    V29, con nombre de índice propio para no confundir con el retirado).
--    Esta vez viene acompañada de un flujo de reutilización real en
--    MetricaIAService (buscar antes de crear) en vez de solo bloquear.
CREATE UNIQUE INDEX IF NOT EXISTS ux_metricas_nombre_global
    ON metricas (lower(trim(nombre)));
