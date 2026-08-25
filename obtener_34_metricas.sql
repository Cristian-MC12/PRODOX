-- =====================================================
-- FASE 16.8.2: OBTENER LAS 34 MÉTRICAS REALES
-- =====================================================
-- NO MODIFICAR DATOS, SOLO CONSULTAR
-- =====================================================

SELECT 
    m.id,
    m.codigo,
    m.nombre,
    m.descripcion,
    m.formula,
    m.procedimiento,
    m.indicador_variable,
    m.unidad,
    m.escala,
    c.nombre as categoria,
    -- Ver si tiene parametrización
    (SELECT COUNT(*) FROM parametrizaciones p WHERE p.metrica_id = m.id) as num_parametrizaciones,
    -- Ver si tiene variables asignadas
    (SELECT COUNT(*) FROM variables_dinamicas vd WHERE vd.metrica_id = m.id) as num_variables
FROM metricas m
LEFT JOIN categorias c ON m.categoria_id = c.id
ORDER BY m.codigo;

-- =====================================================
-- DETALLE DE PARAMETRIZACIONES EXISTENTES
-- =====================================================

SELECT 
    m.codigo,
    m.nombre,
    p.configuracion,
    p.estado,
    p.version,
    p.aprobado_por,
    p.fecha_aprobacion
FROM parametrizaciones p
JOIN metricas m ON p.metrica_id = m.id
WHERE p.estado = 'APROBADO'
ORDER BY m.codigo;

-- =====================================================
-- VARIABLES DINÁMICAS POR MÉTRICA
-- =====================================================

SELECT 
    m.codigo,
    m.nombre as metrica_nombre,
    vd.nombre as variable_nombre,
    vd.tipo_dato,
    vd.descripcion,
    vd.obligatoria
FROM variables_dinamicas vd
JOIN metricas m ON vd.metrica_id = m.id
ORDER BY m.codigo, vd.nombre;
