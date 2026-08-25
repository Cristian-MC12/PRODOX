-- =========================================
-- FASE 16.8.1 - CONSULTA DE DATOS REALES
-- =========================================

-- 1. PROYECTOS EXISTENTES
SELECT 
    id, 
    nombre, 
    descripcion,
    created_at
FROM proyectos
ORDER BY created_at DESC;

-- 2. MÉTRICAS EXISTENTES
SELECT 
    id,
    nombre,
    key_name,
    descripcion,
    categoria
FROM metricas
ORDER BY nombre;

-- 3. SPRINTS EXISTENTES (con proyecto)
SELECT 
    s.id,
    s.nombre,
    s.numero,
    s.fecha_inicio,
    s.fecha_fin,
    p.nombre as proyecto_nombre,
    p.id as proyecto_id
FROM sprints s
JOIN proyectos p ON s.proyecto_id = p.id
ORDER BY s.fecha_inicio DESC;

-- 4. PARAMETRIZACIONES EXISTENTES
SELECT 
    mp.id,
    mp.version,
    mp.status,
    m.nombre as metrica_nombre,
    m.key_name,
    p.nombre as proyecto_nombre,
    mp.configuracion_aprobada_json,
    mp.created_at
FROM metric_parametrizaciones mp
JOIN metricas m ON mp.metrica_id = m.id
JOIN proyectos p ON mp.proyecto_id = p.id
ORDER BY mp.created_at DESC;

-- 5. PARAMETRIZACIONES APROBADAS
SELECT 
    mp.id,
    mp.version,
    m.nombre as metrica_nombre,
    m.key_name,
    p.nombre as proyecto_nombre,
    mp.configuracion_aprobada_json
FROM metric_parametrizaciones mp
JOIN metricas m ON mp.metrica_id = m.id
JOIN proyectos p ON mp.proyecto_id = p.id
WHERE mp.status = 'aprobada'
ORDER BY m.nombre;

-- 6. VARIABLES DINÁMICAS (con parametrización)
SELECT 
    vd.id,
    vd.nombre,
    vd.tipo_dato,
    vd.obligatorio,
    vd.formula_texto,
    vd.formula_json,
    mp.version as param_version,
    m.nombre as metrica_nombre
FROM variables_dinamicas vd
JOIN metric_parametrizaciones mp ON vd.parametrizacion_id = mp.id
JOIN metricas m ON mp.metrica_id = m.id
WHERE mp.status = 'aprobada'
ORDER BY m.nombre, vd.nombre;

-- 7. VALORES REGISTRADOS
SELECT 
    vr.id,
    vd.nombre as variable_nombre,
    vr.valor,
    s.nombre as sprint_nombre,
    p.nombre as proyecto_nombre,
    vr.created_at
FROM valores_registrados vr
JOIN variables_dinamicas vd ON vr.variable_id = vd.id
JOIN sprints s ON vr.sprint_id = s.id
JOIN proyectos p ON s.proyecto_id = p.id
ORDER BY vr.created_at DESC
LIMIT 20;

-- 8. USUARIOS (para pruebas de seguridad)
SELECT 
    id,
    email,
    nombre,
    rol
FROM usuarios
LIMIT 5;

-- 9. MEMBRESÍA PROYECTO-USUARIO (para permisos)
SELECT 
    pm.id,
    u.email,
    p.nombre as proyecto_nombre,
    pm.rol_proyecto
FROM project_members pm
JOIN usuarios u ON pm.usuario_id = u.id
JOIN proyectos p ON pm.proyecto_id = p.id
ORDER BY p.nombre, u.email;

-- 10. VERIFICAR MIGRACIÓN V23
SELECT 
    table_name, 
    column_name, 
    data_type 
FROM information_schema.columns
WHERE table_name = 'resultados_metricas'
ORDER BY ordinal_position;
