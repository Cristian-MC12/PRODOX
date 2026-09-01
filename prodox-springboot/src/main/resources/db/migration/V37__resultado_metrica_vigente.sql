-- Distingue resultado vigente de historico en resultados_metricas (tabla
-- inmutable, append-only): recalcular ya no deja ambiguo cual es "el"
-- resultado de una metrica+sprint+version -- la fila anterior pasa a
-- vigente=false y la nueva queda vigente=true. Ningun dato historico se
-- borra ni se modifica salvo este flag.
ALTER TABLE resultados_metricas
    ADD COLUMN vigente BOOLEAN NOT NULL DEFAULT true;

-- Si ya existian duplicados reales para la misma combinacion (conocido:
-- "cada calculo es una fila nueva", sin unicidad previa), antes de crear el
-- indice unico se deja vigente=true SOLO en la fila mas reciente de cada
-- combinacion -- las demas pasan a vigente=false (quedan intactas como
-- historico). Sin esto, el indice unico de abajo fallaria al crearse sobre
-- datos existentes con duplicados.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY proyecto_id, metrica_id, sprint_id, parametrizacion_version
               ORDER BY calculado_at DESC, id DESC
           ) AS rn
    FROM resultados_metricas
)
UPDATE resultados_metricas r
SET vigente = false
FROM ranked
WHERE r.id = ranked.id
  AND ranked.rn > 1;

-- Evita que un futuro recalculo (o una condicion de carrera) deje mas de un
-- resultado vigente para la misma combinacion proyecto+metrica+sprint+version.
CREATE UNIQUE INDEX idx_resultado_vigente_unico
    ON resultados_metricas (proyecto_id, metrica_id, sprint_id, parametrizacion_version)
    WHERE vigente = true;

COMMENT ON COLUMN resultados_metricas.vigente IS
    'true = resultado vigente para esta combinacion proyecto+metrica+sprint+version. false = historico, reemplazado por un recalculo posterior. Nunca se borra una fila al recalcular.';
