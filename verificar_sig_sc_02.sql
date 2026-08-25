-- Verificar si existe la métrica SIG-SC-02
SELECT id, nombre, categoria 
FROM metricas 
WHERE id = 'sig-sc-02' OR nombre LIKE '%SIG-SC-02%' OR nombre LIKE '%Problemas reportados%';

-- Verificar parametrizaciones existentes
SELECT p.id, p.metrica_id, m.nombre as metrica_nombre, p.status, p.version, p.proyecto_id
FROM metric_parametrizaciones p
LEFT JOIN metricas m ON p.metrica_id = m.id
WHERE p.metrica_id = 'sig-sc-02' OR m.nombre LIKE '%SIG-SC-02%'
ORDER BY p.created_at DESC;

-- Verificar si V24 está aplicada
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'metric_parametrizaciones' 
AND column_name IN ('fuente_academica', 'formula_academica', 'tipo_operacion', 'unidad_resultado');
