-- ============================================================
-- CREAR PARAMETRIZACIÓN APROBADA PARA SIG-SC-02 (PILOTO E2E)
-- COPIAR Y PEGAR EN pgAdmin o psql
-- ============================================================

-- PASO 1: Verificar que NO existe ya
SELECT COUNT(*) as existe
FROM metric_parametrizaciones
WHERE metrica_id = '2ba0cf34-0bec-4e7d-8dc5-40795f050ec9'
  AND proyecto_id = 'fce0340c-74f2-4219-a727-5bae4d842496'
  AND status = 'aprobada';
-- Si existe > 0, NO ejecutar el INSERT

-- PASO 2: Insertar parametrización (SOLO SI PASO 1 = 0)
INSERT INTO metric_parametrizaciones (
    id,
    version,
    factor_id,
    user_id,
    user_email,
    objetivo,
    procedimiento,
    indicador_variable,
    escala,
    metrica_id,
    status,
    proyecto_id,
    frecuencia_captura,
    fuente_academica,
    formula_academica,
    tipo_operacion,
    unidad_resultado,
    created_at
) VALUES (
    gen_random_uuid(),
    1,
    NULL,
    'sm9130109@gmail.com',
    'sm9130109@gmail.com',
    'Medir la cantidad de problemas reportados por el cliente durante el sprint',
    'Al finalizar el sprint, contar el número total de problemas reportados por el cliente. Fórmula: Σ problemas_reportados',
    'problemas_reportados',
    'Número entero >= 0',
    '2ba0cf34-0bec-4e7d-8dc5-40795f050ec9',
    'aprobada',
    'fce0340c-74f2-4219-a727-5bae4d842496',
    'por_sprint',
    'Guerrero-Calvache & Hernández (2024)',
    'Σ problemas_reportados',
    'SUMA',
    'problemas',
    NOW()
);

-- PASO 3: Verificar que se creó correctamente
SELECT 
    id,
    metrica_id,
    status,
    version,
    factor_id,
    formula_academica,
    tipo_operacion,
    unidad_resultado,
    fuente_academica
FROM metric_parametrizaciones
WHERE metrica_id = '2ba0cf34-0bec-4e7d-8dc5-40795f050ec9'
  AND proyecto_id = 'fce0340c-74f2-4219-a727-5bae4d842496'
  AND status = 'aprobada'
ORDER BY created_at DESC
LIMIT 1;

-- Resultado esperado:
-- status = 'aprobada'
-- version = 1
-- factor_id = NULL
-- formula_academica = 'Σ problemas_reportados'
-- tipo_operacion = 'SUMA'
-- unidad_resultado = 'problemas'
-- fuente_academica = 'Guerrero-Calvache & Hernández (2024)'
