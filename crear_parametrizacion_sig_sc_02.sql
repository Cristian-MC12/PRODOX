-- ============================================================
-- SCRIPT: Crear parametrización aprobada para SIG-SC-02
-- Métrica: Problemas reportados por el cliente
-- Autor: Fase 16.9.3 E2E
-- ============================================================

-- PASO 1: Verificar que existe la métrica
SELECT id, nombre FROM metricas WHERE id = 'sig-sc-02';

-- Si no existe, descomentar estas líneas para crearla:
-- INSERT INTO metricas (id, nombre, descripcion, categoria)
-- VALUES ('sig-sc-02', 'SIG-SC-02 — Problemas reportados por el cliente', 
--         'Mide la cantidad de problemas reportados por el cliente', 'Calidad');

-- PASO 2: Obtener el proyecto_id actual
-- Reemplaza 'PROYECTO_ID_AQUI' con el ID real de tu proyecto "Trabajo 1"
-- Para obtenerlo, ejecuta:
SELECT id, nombre FROM proyectos WHERE nombre LIKE '%Trabajo%';

-- PASO 3: Insertar parametrización APROBADA para SIG-SC-02
-- IMPORTANTE: Reemplaza 'PROYECTO_ID_AQUI' con el ID real del paso 2
INSERT INTO metric_parametrizaciones (
    id,
    metrica_id,
    proyecto_id,
    objetivo,
    procedimiento,
    indicador_variable,
    escala,
    frecuencia_captura,
    fuente_academica,
    formula_academica,
    tipo_operacion,
    unidad_resultado,
    status,
    version,
    user_email,
    created_at,
    updated_at
) VALUES (
    gen_random_uuid(),                    -- id generado automáticamente
    'sig-sc-02',                          -- métrica
    'PROYECTO_ID_AQUI',                   -- ⚠️ REEMPLAZAR CON ID REAL
    'Medir la cantidad de problemas reportados por el cliente durante el sprint',  -- objetivo
    'Suma de todos los problemas reportados por el cliente durante el sprint',     -- procedimiento
    'problemas_reportados',               -- indicador_variable
    'Numérica >= 0',                      -- escala
    'por_sprint',                         -- frecuencia_captura
    'Guerrero-Calvache & Hernández (2024)',  -- fuente_academica
    'Σ problemas_reportados',             -- formula_academica
    'SUMA',                               -- tipo_operacion
    'problemas',                          -- unidad_resultado
    'aprobada',                           -- status
    1,                                    -- version
    'sm0130109@gmail.com',                -- user_email
    NOW(),                                -- created_at
    NOW()                                 -- updated_at
)
RETURNING id, metrica_id, status, version;

-- PASO 4: Crear la variable asociada
-- IMPORTANTE: Reemplaza 'PARAMETRIZACION_ID_AQUI' con el ID retornado en el paso 3
INSERT INTO metric_parametrizacion_variables (
    id,
    parametrizacion_id,
    nombre,
    etiqueta,
    tipo,
    unidad,
    requerida,
    frecuencia_captura,
    orden,
    created_at
) VALUES (
    gen_random_uuid(),                    -- id generado automáticamente
    'PARAMETRIZACION_ID_AQUI',            -- ⚠️ REEMPLAZAR con ID del paso 3
    'problemas_reportados',               -- nombre
    'Problemas reportados por el cliente',  -- etiqueta
    'INTEGER',                            -- tipo
    'problemas',                          -- unidad
    true,                                 -- requerida
    'por_sprint',                         -- frecuencia_captura
    1,                                    -- orden
    NOW()                                 -- created_at
)
RETURNING id, nombre, etiqueta;

-- PASO 5: Verificar que todo se creó correctamente
SELECT 
    p.id,
    p.metrica_id,
    p.status,
    p.version,
    p.fuente_academica,
    p.formula_academica,
    p.tipo_operacion,
    p.unidad_resultado,
    COUNT(v.id) as num_variables
FROM metric_parametrizaciones p
LEFT JOIN metric_parametrizacion_variables v ON v.parametrizacion_id = p.id
WHERE p.metrica_id = 'sig-sc-02'
GROUP BY p.id, p.metrica_id, p.status, p.version, p.fuente_academica, 
         p.formula_academica, p.tipo_operacion, p.unidad_resultado
ORDER BY p.created_at DESC
LIMIT 1;
