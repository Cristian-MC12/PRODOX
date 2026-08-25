-- Inspeccionar relación Factor-Métrica-Parametrización
-- 1. Ver factores existentes
SELECT id, nombre, categoria FROM factores LIMIT 5;

-- 2. Ver métricas y su relación con factores
SELECT m.id, m.codigo, m.nombre, m.factor, m.categoria_id 
FROM metricas m 
LIMIT 5;

-- 3. Ver parametrizaciones existentes y sus relaciones
SELECT 
    p.id,
    p.factor_id,
    p.metrica_id,
    f.nombre as factor_nombre,
    m.codigo as metrica_codigo,
    p.status,
    p.version
FROM metric_parametrizaciones p
LEFT JOIN factores f ON p.factor_id = f.id
LEFT JOIN metricas m ON p.metrica_id = m.id
LIMIT 5;
