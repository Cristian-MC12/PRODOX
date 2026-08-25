-- =====================================================
-- FASE 16.8.2: OBTENER MÉTRICAS - CONSULTA SIMPLIFICADA
-- =====================================================

-- Primero, ver estructura de la tabla metricas
SELECT 
    m.id,
    m.codigo,
    m.nombre,
    m.descripcion,
    m.formula,
    m.procedimiento,
    m.indicador_variable,
    m.unidad,
    m.escala
FROM metricas m
ORDER BY m.codigo;

-- Total de métricas
SELECT COUNT(*) as total_metricas FROM metricas;
