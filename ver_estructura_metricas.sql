-- =====================================================
-- FASE 16.8.2: VER ESTRUCTURA REAL DE LA TABLA METRICAS
-- =====================================================

-- Ver todas las columnas disponibles
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'metricas'
ORDER BY ordinal_position;

-- Consulta básica de métricas sin columnas que no existen
SELECT 
    m.id,
    m.codigo,
    m.nombre,
    m.descripcion
FROM metricas m
ORDER BY m.codigo
LIMIT 5;
