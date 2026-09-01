-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V30 — Corrige la unicidad de nombre de métricas: de GLOBAL (V29) a POR
--        PROYECTO (Fase PRODOX AI — revisión post-implementación).
--
-- CONTEXTO:
-- V29 asumió que "duplicado" significaba "mismo nombre en cualquier lugar del
-- catálogo", y creó un índice único GLOBAL sobre metricas.nombre. Eso es
-- incorrecto: MetricaIAService.crearDesdeConfirmacion() crea una fila Metrica
-- nueva y PROPIA por cada Scrum Master que confirma una métrica generada con
-- IA, sin reutilizar filas de otros proyectos. Con el índice global, dos
-- equipos distintos (ej. en el piloto de 30+ equipos) no podían llamar
-- "Velocidad" cada uno a su propia métrica personalizada, aunque nunca se
-- pisan entre sí. La regla funcional real es: sin duplicados DENTRO del mismo
-- proyecto, pero libre entre proyectos distintos.
--
-- Metrica no tenía columna proyecto_id (es intencionalmente un catálogo
-- GLOBAL para las ~40 métricas semilla, referenciadas por múltiples proyectos
-- vía ProyectoMetrica). Esta migración agrega proyecto_id NULLABLE:
--   - NULL para toda métrica existente (las ~40 del catálogo semilla, y
--     cualquier métrica creada con IA antes de esta migración) — no se
--     reasigna nada automáticamente porque no hay evidencia inequívoca de a
--     qué proyecto "pertenecería" cada una ya creada; siguen siendo globales,
--     visibles y usables exactamente igual que hoy.
--   - poblado, desde ahora, solo por MetricaIAService.crearDesdeConfirmacion()
--     con el proyecto que efectivamente confirmó la métrica.
--
-- ON DELETE SET NULL (mismo patrón que V6 en metric_parametrizaciones.proyecto_id,
-- la otra tabla con proyecto_id nullable de este esquema): si el proyecto dueño
-- se borra, la métrica no se borra en cascada — pasa a quedar sin proyecto en
-- vez de perderse, preservando cualquier histórico que la referencie.
--
-- Verificado contra los datos reales antes de escribir esta migración
-- (24/08/2026): 40 métricas en el catálogo, todas sin proyecto_id tras este
-- ALTER (todas NULL) — el nuevo índice parcial (proyecto_id IS NOT NULL) no
-- aplica a ninguna fila existente, por lo que no hay riesgo de colisión al
-- migrar.

-- a) Agregar proyecto_id nullable, con la misma FK ON DELETE SET NULL que V6
--    ya usa para el otro caso de proyecto_id opcional en este esquema.
ALTER TABLE metricas
    ADD COLUMN IF NOT EXISTS proyecto_id UUID REFERENCES proyectos(id) ON DELETE SET NULL;

-- c) Retirar el índice global de V29: bloqueaba nombres iguales entre
--    proyectos distintos, que es un caso válido.
DROP INDEX IF EXISTS ux_metricas_nombre;

-- d) Unicidad real: un mismo proyecto no puede tener dos métricas con el
--    mismo nombre (normalizado); proyectos distintos sí pueden coincidir.
--    Parcial a "proyecto_id IS NOT NULL": las métricas globales (NULL) no
--    entran en esta restricción — Postgres nunca las compara entre sí porque
--    el índice ni siquiera las incluye, evitando el problema de que
--    "todos los NULL son iguales" se aplique aquí sin querer.
CREATE UNIQUE INDEX IF NOT EXISTS ux_metricas_proyecto_nombre
    ON metricas (proyecto_id, lower(trim(nombre)))
    WHERE proyecto_id IS NOT NULL;
