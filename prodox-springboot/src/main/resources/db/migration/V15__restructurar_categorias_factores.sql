-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V15 — Reestructura a 4 categorías (Significado, Flexibilidad, Impacto, Socio-Humano FSH)
--        y agrega nivel de Factor como columna en metricas

-- ============================================================
-- 1. Limpiar datos dependientes
-- ============================================================
DELETE FROM variables;
DELETE FROM proyecto_metricas;
DELETE FROM metricas;
DELETE FROM metrica_categorias;

-- ============================================================
-- 2. Agregar columna 'factor' a metricas si no existe
-- ============================================================
ALTER TABLE metricas
    ADD COLUMN IF NOT EXISTS factor VARCHAR(120);

-- ============================================================
-- 3. Las 4 categorías principales
-- ============================================================
INSERT INTO metrica_categorias (id, nombre) VALUES
    (1, 'Significado'),
    (2, 'Flexibilidad'),
    (3, 'Impacto'),
    (4, 'Socio-Humano FSH');

-- ============================================================
-- 4. Métricas con su factor y categoría
-- ============================================================

-- SIGNIFICADO → Comprensión de los roles de trabajo
INSERT INTO metricas (id, categoria_id, factor, codigo, nombre, descripcion) VALUES
(gen_random_uuid(), 1, 'Comprensión de los roles de trabajo', 'SIG-CRT-01',
    'Comprensión de roles', 'Nivel de comprensión de cada miembro sobre su rol dentro del equipo Scrum.'),
(gen_random_uuid(), 1, 'Comprensión de los roles de trabajo', 'SIG-CRT-02',
    'Claridad de responsabilidades', 'Claridad con que cada integrante conoce sus responsabilidades en el sprint.'),

-- SIGNIFICADO → Satisfacción del cliente
(gen_random_uuid(), 1, 'Satisfacción del cliente', 'SIG-SC-01',
    'Satisfacción del cliente', 'Nivel de satisfacción del cliente con el producto entregado al finalizar el sprint.'),
(gen_random_uuid(), 1, 'Satisfacción del cliente', 'SIG-SC-02',
    'Problemas reportados por el cliente', 'Número de problemas reportados directamente por el cliente durante el sprint.'),

-- SIGNIFICADO → Capacidad de trabajo
(gen_random_uuid(), 1, 'Capacidad de trabajo', 'SIG-CT-01',
    'Capacidad de trabajo', 'Capacidad individual de trabajo de cada miembro del equipo por sprint.'),
(gen_random_uuid(), 1, 'Capacidad de trabajo', 'SIG-CT-02',
    'Calidad (TWQ)', 'Total Work Quality: calidad total del trabajo entregado por el equipo.'),

-- SIGNIFICADO → Velocidad
(gen_random_uuid(), 1, 'Velocidad', 'SIG-VEL-01',
    'Velocidad', 'Story points completados por el equipo durante el sprint.'),
(gen_random_uuid(), 1, 'Velocidad', 'SIG-VEL-02',
    'Impedimentos por sprint', 'Número de impedimentos que bloquearon al equipo durante el sprint.'),

-- SIGNIFICADO → Capacidad del equipo
(gen_random_uuid(), 1, 'Capacidad del equipo', 'SIG-CE-01',
    'Capacidad del equipo', 'Capacidad general del equipo para afrontar el trabajo planificado en el sprint.'),
(gen_random_uuid(), 1, 'Capacidad del equipo', 'SIG-CE-02',
    'Defectos', 'Número total de defectos registrados en el sprint.'),

-- FLEXIBILIDAD → Mejorando el proceso
(gen_random_uuid(), 2, 'Mejorando el proceso', 'FLX-NMP-01',
    'Mejorando el proceso (NMP)', 'New Method Performance: adopción de mejoras en el proceso ágil por sprint.'),
(gen_random_uuid(), 2, 'Mejorando el proceso', 'FLX-NMP-02',
    'Errores por sprint', 'Cantidad de errores detectados durante el sprint.'),

-- FLEXIBILIDAD → Aprendizaje organizacional
(gen_random_uuid(), 2, 'Aprendizaje organizacional', 'FLX-FAT-01',
    'Aprendizaje organizacional (FAT)', 'Failure Analysis Transformation: aprendizaje colectivo del equipo por sprint.'),
(gen_random_uuid(), 2, 'Aprendizaje organizacional', 'FLX-FAT-02',
    'Retrospectivas efectivas', 'Porcentaje de ítems de retrospectiva que generaron acciones concretas.'),

-- FLEXIBILIDAD → Aprendiendo de los fracasos
(gen_random_uuid(), 2, 'Aprendiendo de los fracasos', 'FLX-GAE-01',
    'Aprendiendo de los fracasos (GAE)', 'Growth After Error: capacidad de crecer a partir de los errores cometidos.'),
(gen_random_uuid(), 2, 'Aprendiendo de los fracasos', 'FLX-GAE-02',
    'Deuda técnica gestionada', 'Porcentaje de deuda técnica identificada y gestionada en el sprint.'),

-- IMPACTO → Establecimiento de metas
(gen_random_uuid(), 3, 'Establecimiento de metas', 'IMP-EMG-01',
    'Establecimiento de metas', 'Claridad y cumplimiento de las metas establecidas por sprint.'),
(gen_random_uuid(), 3, 'Establecimiento de metas', 'IMP-EMG-02',
    'Cumplimiento del sprint goal', 'Porcentaje de objetivos del sprint alcanzados al cierre.'),

-- IMPACTO → Manejo de requisitos
(gen_random_uuid(), 3, 'Manejo de requisitos', 'IMP-MR-01',
    'Manejo de requisitos', 'Eficiencia en la gestión y cumplimiento de los requisitos del sprint.'),
(gen_random_uuid(), 3, 'Manejo de requisitos', 'IMP-MR-02',
    'Cambios de alcance por sprint', 'Número de cambios de alcance ocurridos durante el sprint.'),

-- IMPACTO → Calidad
(gen_random_uuid(), 3, 'Calidad', 'IMP-CAL-01',
    'Defectos encontrados', 'Total de defectos encontrados durante el sprint antes de la entrega.'),
(gen_random_uuid(), 3, 'Calidad', 'IMP-CAL-02',
    'Errores en producción', 'Número de errores detectados en producción después del sprint.'),

-- SOCIO-HUMANO FSH → Satisfacción del equipo
(gen_random_uuid(), 4, 'Satisfacción del equipo', 'FSH-SAT-01',
    'Satisfacción del equipo', 'Nivel general de satisfacción de los miembros con el sprint.'),

-- SOCIO-HUMANO FSH → Atmósfera
(gen_random_uuid(), 4, 'Atmósfera', 'FSH-ATM-01',
    'Atmósfera del equipo', 'Percepción del ambiente de trabajo durante el sprint.'),

-- SOCIO-HUMANO FSH → Poder de decisión
(gen_random_uuid(), 4, 'Poder de decisión', 'FSH-POD-01',
    'Autonomía en decisiones', 'Nivel de autonomía que siente cada miembro para tomar decisiones.'),

-- SOCIO-HUMANO FSH → Comunicación
(gen_random_uuid(), 4, 'Comunicación', 'FSH-COM-01',
    'Efectividad de la comunicación', 'Calidad y fluidez de la comunicación dentro del equipo.'),

-- SOCIO-HUMANO FSH → Habilidades sociales
(gen_random_uuid(), 4, 'Habilidades sociales', 'FSH-HAB-01',
    'Habilidades sociales', 'Nivel de habilidades interpersonales demostradas durante el sprint.'),

-- SOCIO-HUMANO FSH → Motivación intrínseca
(gen_random_uuid(), 4, 'Motivación intrínseca', 'FSH-MOT-01',
    'Motivación intrínseca', 'Nivel de motivación interna percibida por cada miembro del equipo.'),

-- SOCIO-HUMANO FSH → Sentimiento de orgullo
(gen_random_uuid(), 4, 'Sentimiento de orgullo', 'FSH-ORG-01',
    'Sentimiento de orgullo', 'Orgullo que siente el equipo por el trabajo entregado en el sprint.'),

-- SOCIO-HUMANO FSH → Configuración del equipo
(gen_random_uuid(), 4, 'Configuración del equipo', 'FSH-CFG-01',
    'Configuración del equipo', 'Adecuación de la composición y estructura del equipo para el proyecto.'),

-- SOCIO-HUMANO FSH → Confianza
(gen_random_uuid(), 4, 'Confianza', 'FSH-CON-01',
    'Confianza en el equipo', 'Nivel de confianza mutua entre los miembros del equipo.'),

-- SOCIO-HUMANO FSH → Liderazgo del equipo
(gen_random_uuid(), 4, 'Liderazgo del equipo', 'FSH-LID-01',
    'Liderazgo del equipo', 'Efectividad del liderazgo ejercido dentro del equipo durante el sprint.'),

-- SOCIO-HUMANO FSH → Compromiso
(gen_random_uuid(), 4, 'Compromiso', 'FSH-CMP-01',
    'Compromiso', 'Nivel de compromiso de cada miembro con los objetivos del sprint.'),
(gen_random_uuid(), 4, 'Compromiso', 'FSH-CMP-02',
    'Estado de ánimo', 'Estado de ánimo general de cada miembro durante el sprint.')

ON CONFLICT (codigo) DO NOTHING;
