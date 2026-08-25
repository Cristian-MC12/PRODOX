-- ============================================================
-- INSPECCIÓN DE DATOS REALES - FASE 16.8
-- SOLO LECTURA - NO MODIFICAR DATOS
-- ============================================================
-- Ejecutar estas queries en PostgreSQL y reportar resultados

-- ============================================================
-- 1. SCHEMA DE metric_parametrizaciones
-- ============================================================
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns 
WHERE table_name = 'metric_parametrizaciones'
ORDER BY ordinal_position;

-- ============================================================
-- 2. PARAMETRIZACIONES - TODAS
-- ============================================================
SELECT 
    id,
    version,
    objetivo,
    procedimiento,
    indicador_variable,
    escala,
    status,
    proyecto_id,
    metrica_id,
    created_at
FROM metric_parametrizaciones
ORDER BY created_at DESC
LIMIT 10;

-- ============================================================
-- 3. PARAMETRIZACIONES APROBADAS - CON JSON
-- ============================================================
SELECT 
    id,
    version,
    objetivo,
    procedimiento,
    indicador_variable,
    escala,
    propuesta_ia_json,
    configuracion_aprobada_json,
    status,
    proyecto_id,
    metrica_id
FROM metric_parametrizaciones
WHERE status = 'aprobada'
ORDER BY version DESC
LIMIT 5;

-- ============================================================
-- 4. VARIABLES - TODAS
-- ============================================================
SELECT 
    id,
    nombre,
    descripcion,
    tipo_dato,
    formula_texto,
    formula_json,
    parametrizacion_id,
    parametrizacion_version,
    proyecto_id,
    metrica_id
FROM variables
ORDER BY created_at DESC
LIMIT 20;

-- ============================================================
-- 5. VALORES REGISTRADOS
-- ============================================================
SELECT 
    rv.id,
    rv.variable_id,
    v.nombre AS variable_nombre,
    rv.sprint_id,
    rv.valor_num,
    rv.valor_texto,
    rv.valor_bool,
    rv.registrado_at
FROM registro_valores rv
JOIN variables v ON v.id = rv.variable_id
ORDER BY rv.registrado_at DESC
LIMIT 20;

-- ============================================================
-- 6. CONTEOS GENERALES
-- ============================================================
SELECT 
    'metric_parametrizaciones' as tabla,
    COUNT(*) as total
FROM metric_parametrizaciones
UNION ALL
SELECT 
    'parametrizaciones_aprobadas' as tabla,
    COUNT(*) as total
FROM metric_parametrizaciones
WHERE status = 'aprobada'
UNION ALL
SELECT 
    'variables' as tabla,
    COUNT(*) as total
FROM variables
UNION ALL
SELECT 
    'registro_valores' as tabla,
    COUNT(*) as total
FROM registro_valores;

-- ============================================================
-- 7. VERSIONES POR MÉTRICA
-- ============================================================
SELECT 
    metrica_id,
    proyecto_id,
    version,
    status,
    LEFT(procedimiento, 100) as procedimiento_inicio,
    LEFT(indicador_variable, 100) as indicador_inicio
FROM metric_parametrizaciones
WHERE metrica_id IS NOT NULL
ORDER BY metrica_id, proyecto_id, version;

-- ============================================================
-- 8. FÓRMULAS - Verificar si existen valores
-- ============================================================
-- Variables con formula_texto
SELECT 
    id,
    nombre,
    formula_texto
FROM variables
WHERE formula_texto IS NOT NULL
LIMIT 5;

-- Variables con formula_json
SELECT 
    id,
    nombre,
    formula_json
FROM variables
WHERE formula_json IS NOT NULL
LIMIT 5;

-- Parametrizaciones con configuracion_aprobada_json
SELECT 
    id,
    version,
    status,
    configuracion_aprobada_json
FROM metric_parametrizaciones
WHERE configuracion_aprobada_json IS NOT NULL
LIMIT 5;

-- ============================================================
-- 9. EJEMPLO COMPLETO - Una parametrización con sus variables
-- ============================================================
SELECT 
    'PARAMETRIZACION' as tipo,
    mp.id::text as identificador,
    mp.version::text as info1,
    mp.procedimiento as info2,
    mp.indicador_variable as info3
FROM metric_parametrizaciones mp
WHERE mp.status = 'aprobada'
LIMIT 1

UNION ALL

SELECT 
    'VARIABLE' as tipo,
    v.id::text as identificador,
    v.nombre as info1,
    v.formula_texto as info2,
    v.formula_json::text as info3
FROM variables v
WHERE v.parametrizacion_id IN (
    SELECT id FROM metric_parametrizaciones WHERE status = 'aprobada' LIMIT 1
);

-- ============================================================
-- 10. TEXTO COMPLETO DE PROCEDIMIENTOS E INDICADORES
-- ============================================================
-- Ver los primeros 3 procedimientos completos para análisis
SELECT 
    id,
    version,
    status,
    '=== PROCEDIMIENTO ===' as separador1,
    procedimiento,
    '=== INDICADOR VARIABLE ===' as separador2,
    indicador_variable,
    '=== ESCALA ===' as separador3,
    escala
FROM metric_parametrizaciones
WHERE status = 'aprobada'
ORDER BY created_at DESC
LIMIT 3;

-- ============================================================
-- 11. JSON COMPLETO - Si existe
-- ============================================================
SELECT 
    id,
    version,
    configuracion_aprobada_json
FROM metric_parametrizaciones
WHERE configuracion_aprobada_json IS NOT NULL
LIMIT 1;

-- ============================================================
-- FIN DE INSPECCIÓN
-- ============================================================
-- REPORTAR TODOS LOS RESULTADOS DE ESTAS QUERIES
