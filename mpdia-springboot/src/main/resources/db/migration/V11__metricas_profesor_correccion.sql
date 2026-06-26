-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V11 — Categorías y métricas exactas del profesor + selección por proyecto + tipo_indicador en variables

-- ============================================================
-- 1. REEMPLAZAR categorías con las del profesor
-- ============================================================
-- Limpiar métricas antiguas (las de V10 no coinciden)
DELETE FROM variables;
DELETE FROM metricas;
DELETE FROM metrica_categorias;

INSERT INTO metrica_categorias (id, nombre) VALUES
    (1, 'Calidad'),
    (2, 'Productividad'),
    (3, 'Cumplimiento'),
    (4, 'Flexibilidad'),
    (5, 'Sociohumano')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 2. MÉTRICAS EXACTAS DEL PROFESOR
-- ============================================================
INSERT INTO metricas (id, categoria_id, codigo, nombre, descripcion) VALUES
-- Calidad
(gen_random_uuid(), 1, 'CAL-DEF',  'Defectos',
    'Número total de defectos registrados en el sprint.'),
(gen_random_uuid(), 1, 'CAL-ERR',  'Errores por sprint',
    'Cantidad de errores detectados durante el sprint.'),
(gen_random_uuid(), 1, 'CAL-PRB',  'Problemas reportados por el cliente',
    'Número de problemas reportados directamente por el cliente.'),
(gen_random_uuid(), 1, 'CAL-IMP',  'Impedimentos por sprint',
    'Número de impedimentos que bloquearon al equipo durante el sprint.'),
(gen_random_uuid(), 1, 'CAL-TWQ',  'Calidad (TWQ)',
    'Total Work Quality: calidad total del trabajo entregado.'),
-- Productividad
(gen_random_uuid(), 2, 'PRD-SC',   'Satisfacción del cliente',
    'Nivel de satisfacción del cliente con el producto entregado.'),
(gen_random_uuid(), 2, 'PRD-CRT',  'Comprensión de roles',
    'Qué tan bien el equipo comprende y ejecuta sus roles.'),
(gen_random_uuid(), 2, 'PRD-CE',   'Capacidad del equipo',
    'Capacidad general del equipo para afrontar el trabajo planificado.'),
(gen_random_uuid(), 2, 'PRD-CT',   'Capacidad de trabajo',
    'Capacidad individual de trabajo de cada miembro.'),
(gen_random_uuid(), 2, 'PRD-VEL',  'Velocidad',
    'Story points completados por sprint.'),
-- Cumplimiento
(gen_random_uuid(), 3, 'CMP-EMG',  'Establecimiento de metas',
    'Claridad y cumplimiento de las metas establecidas por sprint.'),
(gen_random_uuid(), 3, 'CMP-MR',   'Manejo de requisitos',
    'Eficiencia en la gestión y cumplimiento de los requisitos.'),
-- Flexibilidad
(gen_random_uuid(), 4, 'FLX-NMP',  'Mejorando el proceso (NMP)',
    'New Method Performance: adopción de mejoras en el proceso ágil.'),
(gen_random_uuid(), 4, 'FLX-FAT',  'Aprendizaje organizacional (FAT)',
    'Failure Analysis Transformation: aprendizaje colectivo del equipo.'),
(gen_random_uuid(), 4, 'FLX-GAE',  'Aprendiendo de los fracasos (GAE)',
    'Growth After Error: capacidad de crecer a partir de los errores.'),
-- Sociohumano
(gen_random_uuid(), 5, 'SOC-BIE',  'Bienestar',
    'Nivel de bienestar percibido por cada integrante del equipo.'),
(gen_random_uuid(), 5, 'SOC-ANI',  'Estado de ánimo',
    'Estado de ánimo general de cada miembro durante el sprint.')
ON CONFLICT (codigo) DO NOTHING;

-- ============================================================
-- 3. SELECCIÓN DE MÉTRICAS POR PROYECTO
-- ============================================================
CREATE TABLE IF NOT EXISTS proyecto_metricas (
    proyecto_id  UUID     NOT NULL REFERENCES proyectos(id) ON DELETE CASCADE,
    metrica_id   UUID     NOT NULL REFERENCES metricas(id) ON DELETE CASCADE,
    aprobada     BOOLEAN  NOT NULL DEFAULT FALSE,
    aprobada_por VARCHAR(255),
    aprobada_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (proyecto_id, metrica_id)
);

-- ============================================================
-- 4. AGREGAR tipo_indicador a variables
-- ============================================================
ALTER TABLE variables
    ADD COLUMN IF NOT EXISTS tipo_indicador VARCHAR(20) NOT NULL DEFAULT 'calidad'
        CHECK (tipo_indicador IN ('calidad','productividad','cumplimiento','flexibilidad','sociohumano'));
