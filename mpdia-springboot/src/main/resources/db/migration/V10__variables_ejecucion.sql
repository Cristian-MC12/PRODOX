-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V10 — Variables, registro de valores, métricas del profesor, estados de sprint

-- ============================================================
-- 1. PROYECTOS: agregar numero_sprints y fecha_inicio
-- ============================================================
ALTER TABLE proyectos
    ADD COLUMN IF NOT EXISTS numero_sprints  INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS fecha_inicio    DATE;

-- ============================================================
-- 2. SPRINTS: ampliar estados + campos de cierre
-- ============================================================
-- Paso 1: Eliminar el check original que solo permite 'activo' y 'finalizado'
ALTER TABLE sprints DROP CONSTRAINT IF EXISTS sprints_estado_check;

-- Paso 2: Migrar datos existentes antes de agregar el nuevo constraint
UPDATE sprints SET estado = 'en_ejecucion' WHERE estado = 'activo';

-- Paso 3: Agregar nuevo constraint con todos los estados requeridos
ALTER TABLE sprints
    ADD CONSTRAINT sprints_estado_check
    CHECK (estado IN ('pendiente','en_ejecucion','finalizado','reabierto'));

ALTER TABLE sprints
    ADD COLUMN IF NOT EXISTS cerrado_por    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS cerrado_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reabierto_por  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reabierto_at   TIMESTAMPTZ;

-- ============================================================
-- 3. MÉTRICAS DEL PROFESOR (reemplaza factors/indicators)
-- ============================================================

-- Categorías de métricas
CREATE TABLE IF NOT EXISTS metrica_categorias (
    id     SMALLINT    PRIMARY KEY,
    nombre VARCHAR(60) NOT NULL UNIQUE
);

INSERT INTO metrica_categorias VALUES
    (1, 'Significado'),
    (2, 'Impacto'),
    (3, 'Flexibilidad')
ON CONFLICT DO NOTHING;

-- Métricas fijas del profesor (Tablas 8, 9 y 10 del documento)
CREATE TABLE IF NOT EXISTS metricas (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    categoria_id SMALLINT   NOT NULL REFERENCES metrica_categorias(id),
    codigo      VARCHAR(20) NOT NULL UNIQUE,
    nombre      VARCHAR(120) NOT NULL,
    descripcion TEXT
);

INSERT INTO metricas (id, categoria_id, codigo, nombre, descripcion) VALUES
-- Tabla 8 — Significado
(gen_random_uuid(), 1, 'SIG-SC',  'Satisfacción del cliente',      'Mide el nivel de satisfacción del cliente con el producto entregado en cada sprint.'),
(gen_random_uuid(), 1, 'SIG-CRT', 'Comprensión de roles de trabajo','Evalúa qué tan bien el equipo comprende y ejecuta sus roles definidos.'),
(gen_random_uuid(), 1, 'SIG-CE',  'Capacidad del equipo',           'Mide la capacidad general del equipo para afrontar el trabajo planificado.'),
(gen_random_uuid(), 1, 'SIG-CT',  'Capacidad de trabajo',           'Mide la capacidad individual de trabajo de cada miembro del equipo.'),
(gen_random_uuid(), 1, 'SIG-VEL', 'Velocidad',                      'Cantidad de puntos de historia completados por sprint.'),
-- Tabla 9 — Impacto
(gen_random_uuid(), 2, 'IMP-EMG', 'Establecimiento de metas',       'Evalúa la claridad y el cumplimiento de las metas establecidas por sprint.'),
(gen_random_uuid(), 2, 'IMP-MR',  'Manejo de los requisitos',       'Mide la eficiencia en la gestión y cumplimiento de los requisitos del proyecto.'),
(gen_random_uuid(), 2, 'IMP-TWQ', 'Calidad (TWQ)',                   'Total Work Quality: calidad total del trabajo entregado en cada sprint.'),
-- Tabla 10 — Flexibilidad
(gen_random_uuid(), 3, 'FLX-NMP', 'Mejorando el proceso (NMP)',     'New Method Performance: mide la adopción de mejoras en el proceso ágil.'),
(gen_random_uuid(), 3, 'FLX-FAT', 'Aprendizaje organizacional (FAT)','Failure Analysis Transformation: evalúa el aprendizaje colectivo del equipo.'),
(gen_random_uuid(), 3, 'FLX-GAE', 'Aprendiendo de los fracasos (GAE)','Growth After Error: capacidad del equipo de crecer a partir de los errores.')
ON CONFLICT (codigo) DO NOTHING;

-- ============================================================
-- 4. VARIABLES (metadatos derivados de los indicadores seleccionados)
-- ============================================================
CREATE TABLE IF NOT EXISTS variables (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_id     UUID        NOT NULL REFERENCES proyectos(id) ON DELETE CASCADE,
    metrica_id      UUID        NOT NULL REFERENCES metricas(id),
    nombre          VARCHAR(120) NOT NULL,
    descripcion     TEXT,
    tipo_alcance    VARCHAR(20) NOT NULL DEFAULT 'grupal'
                        CHECK (tipo_alcance IN ('grupal','individual')),
    frecuencia      VARCHAR(20) NOT NULL DEFAULT 'por_sprint'
                        CHECK (frecuencia IN ('diaria','semanal','por_sprint')),
    cardinalidad    VARCHAR(20) NOT NULL DEFAULT 'unico'
                        CHECK (cardinalidad IN ('unico','multiple')),
    tipo_dato       VARCHAR(20) NOT NULL DEFAULT 'numerico'
                        CHECK (tipo_dato IN ('numerico','texto','booleano','escala')),
    escala_min      NUMERIC,
    escala_max      NUMERIC,
    activa          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (proyecto_id, metrica_id)
);

-- ============================================================
-- 5. REGISTRO DE VALORES (fase de ejecución)
-- ============================================================
CREATE TABLE IF NOT EXISTS registro_valores (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    variable_id  UUID        NOT NULL REFERENCES variables(id) ON DELETE CASCADE,
    sprint_id    UUID        NOT NULL REFERENCES sprints(id) ON DELETE CASCADE,
    user_id      VARCHAR(255) NOT NULL,
    valor_num    NUMERIC,
    valor_texto  TEXT,
    valor_bool   BOOLEAN,
    observacion  TEXT,
    registrado_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_registro_valores_sprint  ON registro_valores(sprint_id);
CREATE INDEX IF NOT EXISTS idx_registro_valores_variable ON registro_valores(variable_id);

-- ============================================================
-- 6. EVALUACIÓN: resumen por sprint (calculado / desnormalizado)
-- ============================================================
CREATE TABLE IF NOT EXISTS evaluacion_sprint (
    id                  UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_id         UUID    NOT NULL REFERENCES proyectos(id) ON DELETE CASCADE,
    sprint_id           UUID    NOT NULL REFERENCES sprints(id) ON DELETE CASCADE,
    metrica_id          UUID    NOT NULL REFERENCES metricas(id),
    promedio            NUMERIC,
    total_registros     INTEGER NOT NULL DEFAULT 0,
    calculado_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (sprint_id, metrica_id)
);
