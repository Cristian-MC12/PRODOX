-- Query 1: Ver parametrizaciones aprobadas (sin columna version)
SELECT 
    id, 
    objetivo, 
    procedimiento, 
    indicador_variable,
    escala,
    status,
    metrica_id,
    proyecto_id,
    created_at
FROM metric_parametrizaciones 
WHERE status = 'aprobada'
ORDER BY created_at DESC
LIMIT 5;

-- Query 2: Ver estructura de una parametrización específica
SELECT *
FROM metric_parametrizaciones 
WHERE status = 'aprobada'
LIMIT 1;

-- Query 3: Ver variables existentes
SELECT 
    id,
    nombre,
    descripcion,
    tipo_dato,
    formula_texto,
    metrica_id,
    proyecto_id
FROM variables
LIMIT 5;

-- Query 4: Ver valores registrados
SELECT 
    rv.id,
    rv.valor_num,
    rv.valor_texto,
    rv.valor_bool,
    rv.sprint_id,
    rv.registrado_at
FROM registro_valores rv
LIMIT 5;

-- Query 5: Verificar si existen columnas de versionado
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'metric_parametrizaciones'
ORDER BY ordinal_position;
