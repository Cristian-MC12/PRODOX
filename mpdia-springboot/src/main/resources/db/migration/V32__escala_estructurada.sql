-- Escala estructurada de parametrización/variable
-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- Fecha: 2026-08-28
--
-- Hasta ahora la escala de una parametrización era un texto libre
-- (metric_parametrizaciones.escala, VARCHAR 255) sin ninguna estructura, y la
-- traducción a los límites numéricos reales de Variable dependía de una
-- heurística de regex frágil (\d+-\d+) que fallaba en la mayoría de los
-- casos, dejando Ejecución sin ninguna validación de rango real.
--
-- Estas columnas nuevas son la representación estructurada real. El campo de
-- texto libre `escala` se conserva sin cambios (compatibilidad/visualización
-- histórica) — la lógica funcional pasa a basarse exclusivamente en las
-- columnas nuevas.
--
-- Todas nullable a propósito: las parametrizaciones/variables históricas no
-- tienen esta información y NO se intenta inferirla (no hay forma segura de
-- derivar min/max/paso desde texto libre arbitrario) — quedan explícitamente
-- como "no estructurada" en vez de fingir una estructura inventada.

ALTER TABLE metric_parametrizaciones
    ADD COLUMN escala_tipo        VARCHAR(30),
    ADD COLUMN escala_min         NUMERIC,
    ADD COLUMN escala_max         NUMERIC,
    ADD COLUMN escala_paso        NUMERIC,
    ADD COLUMN escala_sin_limite  BOOLEAN,
    ADD COLUMN escala_descripcion TEXT;

ALTER TABLE variables
    ADD COLUMN escala_tipo       VARCHAR(30),
    ADD COLUMN escala_paso       NUMERIC,
    ADD COLUMN escala_sin_limite BOOLEAN;

COMMENT ON COLUMN metric_parametrizaciones.escala_tipo IS
'NUMERICA_ENTERA | NUMERICA_DECIMAL. NULL = parametrización histórica sin escala estructurada (no inferir, no asumir).';

COMMENT ON COLUMN metric_parametrizaciones.escala_min IS
'Valor mínimo permitido. Obligatorio cuando escala_tipo no es NULL.';

COMMENT ON COLUMN metric_parametrizaciones.escala_max IS
'Valor máximo permitido. NULL cuando escala_sin_limite=true; obligatorio en caso contrario.';

COMMENT ON COLUMN metric_parametrizaciones.escala_paso IS
'Incremento permitido entre valores válidos (ej. 1 para enteros, 0.01 para dos decimales).';

COMMENT ON COLUMN metric_parametrizaciones.escala_sin_limite IS
'true = sin máximo superior (ej. conteos). Distingue explícitamente de escala_max simplemente ausente/no interpretada.';

COMMENT ON COLUMN metric_parametrizaciones.escala_descripcion IS
'Descripción humana del significado de los valores (ej. "0 = Muy malo; 10 = Excelente"), independiente del texto libre `escala`.';

COMMENT ON COLUMN variables.escala_tipo IS
'Copiado de metric_parametrizaciones.escala_tipo al aprobar. NULL = variable sin escala estructurada (Ejecución no restringe tipo/paso, solo min/max si existen).';

COMMENT ON COLUMN variables.escala_paso IS
'Copiado de metric_parametrizaciones.escala_paso al aprobar.';

COMMENT ON COLUMN variables.escala_sin_limite IS
'Copiado de metric_parametrizaciones.escala_sin_limite al aprobar.';
