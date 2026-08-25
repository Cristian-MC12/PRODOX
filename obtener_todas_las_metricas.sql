-- =====================================================
-- FASE 16.8.2: OBTENER LAS 34 MÉTRICAS COMPLETAS
-- =====================================================

SELECT 
    m.id,
    m.codigo,
    m.nombre,
    m.descripcion,
    m.factor,
    m.categoria_id
FROM metricas m
ORDER BY m.codigo;
