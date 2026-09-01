-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V29 — Evita nombres de métrica duplicados en el catálogo (Fase PRODOX AI)
--
-- CONTEXTO:
-- Metrica es un catálogo GLOBAL (no tiene proyecto_id: la asociación a un
-- proyecto se hace vía ProyectoMetrica/MetricParametrizacion), y hasta ahora
-- no existía ninguna restricción sobre metricas.nombre en ningún nivel (BD,
-- entidad, ni MetricaIAService.crearDesdeConfirmacion(), el único punto que
-- inserta filas nuevas en tiempo de ejecución). El alcance correcto de la
-- unicidad es por lo tanto GLOBAL sobre nombre, igual que ya lo es "codigo"
-- (metricas_codigo_key, ver V1/entidad Metrica).
--
-- Se compara sobre lower(trim(nombre)) para tratar como duplicado un nombre
-- que solo difiere en mayúsculas/espacios, que es la noción funcional real
-- de "mismo nombre" para un Scrum Master eligiendo del catálogo.
--
-- Verificado contra los datos reales antes de escribir esta migración
-- (23/08/2026): 40 métricas en el catálogo, 0 duplicados por nombre exacto
-- ni por lower(trim(nombre)) — la migración no requiere limpieza previa de
-- datos existentes.

CREATE UNIQUE INDEX IF NOT EXISTS ux_metricas_nombre
    ON metricas (lower(trim(nombre)));
