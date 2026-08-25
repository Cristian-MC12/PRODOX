-- ============================================================
-- CREAR PARAMETRIZACIÓN APROBADA PARA SIG-SC-02
-- Fase 16.9.3-B — Flujo Real de Planeación
-- ============================================================
-- IMPORTANTE:
-- - factor_id es NULLABLE (no se requiere Factor)
-- - Campos académicos V24 están presentes
-- - user_id debe corresponder al email del usuario autenticado
-- ============================================================

-- PASO 1: Insertar parametrización APROBADA (SIN factor_id)
INSERT INTO metric_parametrizaciones (
    id,
    version,
    factor_id,                           -- NULL (opcional)
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
    1,                                   -- versión 1
    NULL,                                -- factor_id es nullable
    'sm9130109@gmail.com',               -- user_id
    'sm9130109@gmail.com',               -- user_email
    'Medir la cantidad de problemas reportados por el cliente durante el sprint',
    'Al finalizar el sprint, contar el número total de problemas reportados por el cliente. Fórmula: Σ problemas_reportados',
    'problemas_reportados',
    'Número entero >= 0',
    '2ba0cf34-0bec-4e7d-8dc5-40795f050ec9',  -- metrica_id de SIG-SC-02
    'aprobada',                          -- status
    'fce0340c-74f2-4219-a727-5bae4d842496',  -- proyecto_id de "Trabajo 1"
    'por_sprint',
    'Guerrero-Calvache & Hernández (2024)',
    'Σ problemas_reportados',
    'SUMA',
    'problemas',
    NOW()
)
RETURNING id, metrica_id, status, version;

-- PASO 2: Verificar parametrización creada
SELECT 
    p.id,
    p.metrica_id,
    m.nombre AS metrica_nombre,
    p.status,
    p.version,
    p.factor_id,                         -- Debe ser NULL
    p.fuente_academica,
    p.formula_academica,
    p.tipo_operacion,
    p.unidad_resultado,
    p.created_at
FROM metric_parametrizaciones p
JOIN metricas m ON p.metrica_id = m.id
WHERE p.metrica_id = '2ba0cf34-0bec-4e7d-8dc5-40795f050ec9'
  AND p.proyecto_id = 'fce0340c-74f2-4219-a727-5bae4d842496'
ORDER BY p.created_at DESC
LIMIT 1;

-- PASO 3: Verificar que NO hay conflicto de FK
-- (factor_id es NULL, por lo tanto no hay conflicto)

-- PASO 4: Probar endpoint REST
-- GET http://localhost:8080/api/parametrizacion/ultima-aprobada?metricaId=2ba0cf34-0bec-4e7d-8dc5-40795f050ec9&proyectoId=fce0340c-74f2-4219-a727-5bae4d842496
