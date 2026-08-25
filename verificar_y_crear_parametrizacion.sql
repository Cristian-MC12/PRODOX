-- ============================================================
-- VERIFICAR Y CREAR PARAMETRIZACIÓN SIG-SC-02 (PILOTO E2E)
-- Fase 16.9.3-C
-- ============================================================

-- PASO 1: VERIFICAR SI YA EXISTE
SELECT 
    id,
    metrica_id,
    proyecto_id,
    factor_id,
    status,
    version,
    formula_academica,
    tipo_operacion,
    unidad_resultado,
    created_at
FROM metric_parametrizaciones
WHERE metrica_id = '2ba0cf34-0bec-4e7d-8dc5-40795f050ec9'
  AND proyecto_id = 'fce0340c-74f2-4219-a727-5bae4d842496'
ORDER BY created_at DESC;

-- Si la consulta anterior retorna registros:
-- - Si status='aprobada' → NO ejecutar INSERT, usar la existente
-- - Si status='propuesta' o 'inactiva' → Determinar siguiente versión

-- PASO 2: SOLO SI NO EXISTE, INSERTAR
-- (Descomentar SOLO si el PASO 1 retorna vacío)

/*
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
)
RETURNING id, metrica_id, status, version;
*/
