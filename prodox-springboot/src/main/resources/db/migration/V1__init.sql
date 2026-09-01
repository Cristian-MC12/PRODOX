-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- ============================================================
-- MPDIA — PostgreSQL Schema
-- ============================================================

-- Usuarios de la aplicación
CREATE TABLE IF NOT EXISTS app_users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL DEFAULT 'scrum_member',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Catálogo de factores de medición
CREATE TABLE IF NOT EXISTS factors (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL,
    category    VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Selección de factores por sprint
CREATE TABLE IF NOT EXISTS sprint_factor_selections (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    factor_id   UUID         NOT NULL REFERENCES factors(id) ON DELETE CASCADE,
    sprint_name VARCHAR(255) NOT NULL DEFAULT 'Sprint Actual',
    selected_by VARCHAR(255) NOT NULL,
    selected_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (factor_id, sprint_name)
);

-- Indicadores generados
CREATE TABLE IF NOT EXISTS indicators (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    factor_id   UUID         NOT NULL REFERENCES factors(id) ON DELETE CASCADE,
    value       NUMERIC      NOT NULL,
    unit        VARCHAR(50),
    measured_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    status      VARCHAR(50)  NOT NULL DEFAULT 'pendiente',
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Configuración del Copiloto por usuario
CREATE TABLE IF NOT EXISTS copilot_config (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      VARCHAR(255) NOT NULL UNIQUE,
    tool         VARCHAR(50)  NOT NULL DEFAULT 'jira',
    url          VARCHAR(500) NOT NULL,
    api_key      VARCHAR(500) NOT NULL,
    frequency    VARCHAR(50)  NOT NULL DEFAULT 'daily',
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_sync_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Datos semilla para demo de tesis
-- ============================================================

-- Factores técnicos de productividad
INSERT INTO factors (name, description, category) VALUES
    ('Velocidad del Equipo',     'Story points completados por sprint vs. los planificados.',                   'Productividad'),
    ('Calidad de Código',        'Porcentaje de código sin deuda técnica según análisis estático.',             'Calidad'),
    ('Cumplimiento de Objetivos','Porcentaje de objetivos del sprint alcanzados al cierre.',                    'Cumplimiento'),
    ('Cobertura de Pruebas',     'Porcentaje de líneas de código cubiertas por pruebas automatizadas.',        'Calidad'),
    ('Lead Time',                'Tiempo promedio desde que una tarea entra al backlog hasta que se entrega.', 'Productividad'),
    ('Defectos por Sprint',      'Número de defectos reportados durante el sprint.',                           'Calidad')
ON CONFLICT DO NOTHING;

-- Factores sociohumanos de productividad
INSERT INTO factors (name, description, category) VALUES
    ('Satisfacción del Equipo',      'Nivel de satisfacción general de los integrantes con el trabajo realizado en el sprint, medido mediante encuesta al cierre.',          'Sociohumano'),
    ('Motivación del Equipo',        'Grado de motivación e implicación de los miembros del equipo durante el sprint, evaluado mediante autoevaluación.',                    'Sociohumano'),
    ('Comunicación Interna',         'Calidad y frecuencia de la comunicación entre los integrantes del equipo Scrum durante el sprint.',                                    'Sociohumano'),
    ('Colaboración entre Miembros',  'Nivel de trabajo colaborativo y apoyo mutuo entre los integrantes del equipo durante el sprint.',                                      'Sociohumano'),
    ('Carga de Trabajo Percibida',   'Percepción del equipo sobre la distribución equitativa y razonable de la carga de trabajo durante el sprint.',                        'Sociohumano'),
    ('Bienestar del Equipo',         'Estado general de bienestar físico y emocional de los integrantes, considerando el equilibrio entre trabajo y vida personal.',         'Sociohumano'),
    ('Rotación de Personal',         'Número de cambios en la composición del equipo durante el sprint que afectan la continuidad y el conocimiento compartido.',            'Sociohumano'),
    ('Confianza en el Equipo',       'Nivel de confianza mutua entre los integrantes del equipo para delegar tareas, pedir ayuda y compartir errores sin temor.',           'Sociohumano')
ON CONFLICT DO NOTHING;
