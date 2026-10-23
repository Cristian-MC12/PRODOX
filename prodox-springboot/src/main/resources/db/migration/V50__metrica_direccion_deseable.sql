-- Autor: Cristian Santiago Martinez Cordoba — PRODOX
-- V50 — Añadir dirección deseable de métricas para interpretación de tendencias

-- ============================================================
-- CONTEXTO Y MOTIVACIÓN
-- ============================================================
-- Los insights de IA (AIInsightsService) clasifican cambios en métricas como
-- "positivos" o "negativos" sin conocer la dirección deseable de cada métrica.
-- Esto puede generar interpretaciones incorrectas: por ejemplo, un aumento de
-- "Defectos" NO es positivo aunque la tendencia sea UP.
--
-- Esta migración introduce dos campos OPCIONALES en la tabla metricas:
--
-- 1. direccion_deseable: indica cómo debe interpretarse un cambio en la métrica
--    - MAYOR_MEJOR: valores mayores pueden ser favorables
--    - MENOR_MEJOR: valores menores pueden ser favorables
--    - OBJETIVO: la métrica busca alcanzar un valor objetivo específico
--    - NULL: dirección NO configurada (interpretación neutral obligatoria)
--
-- 2. valor_objetivo: valor numérico objetivo (solo válido cuando direccion_deseable='OBJETIVO')
--
-- COMPATIBILIDAD CON DATOS EXISTENTES:
-- - Ambos campos son NULLABLE
-- - Todas las métricas existentes quedarán con direccion_deseable=NULL inicialmente
-- - No se asignan valores por defecto semánticos
-- - Las métricas sin dirección configurada deben recibir interpretación NEUTRAL

-- ============================================================
-- 1. AGREGAR COLUMNAS
-- ============================================================

ALTER TABLE metricas 
ADD COLUMN direccion_deseable VARCHAR(20) 
CHECK (direccion_deseable IN ('MAYOR_MEJOR', 'MENOR_MEJOR', 'OBJETIVO'));

ALTER TABLE metricas 
ADD COLUMN valor_objetivo NUMERIC(15,4);

-- ============================================================
-- 2. VALIDACIÓN DE COHERENCIA
-- ============================================================
-- Si direccion_deseable='OBJETIVO', entonces valor_objetivo debe ser NOT NULL.
-- Si direccion_deseable IN ('MAYOR_MEJOR', 'MENOR_MEJOR'), entonces valor_objetivo debe ser NULL.
-- Esta validación se implementa en el backend (entity Metrica) mediante @PrePersist/@PreUpdate,
-- no como constraint de BD, para permitir mensajes de error más descriptivos.

-- ============================================================
-- 3. ÍNDICE PARA CONSULTAS DE INSIGHTS
-- ============================================================
-- Índice parcial para métricas con dirección configurada (usadas en generación de insights)
CREATE INDEX idx_metricas_direccion_configurada 
    ON metricas(direccion_deseable) 
    WHERE direccion_deseable IS NOT NULL;

-- ============================================================
-- 4. COMENTARIOS DE DOCUMENTACIÓN
-- ============================================================

COMMENT ON COLUMN metricas.direccion_deseable IS 
    'Dirección deseable para interpretación de tendencias. MAYOR_MEJOR: valores mayores pueden ser favorables. MENOR_MEJOR: valores menores pueden ser favorables. OBJETIVO: busca alcanzar valor_objetivo. NULL: dirección no configurada (interpretación neutral).';

COMMENT ON COLUMN metricas.valor_objetivo IS 
    'Valor numérico objetivo cuando direccion_deseable=OBJETIVO. Debe ser NULL para otras direcciones. Precision 15 dígitos, 4 decimales.';

-- ============================================================
-- 5. VERIFICACIÓN POST-MIGRACIÓN
-- ============================================================
-- Esta migración NO modifica datos existentes.
-- Para verificar que todas las métricas existentes tienen direccion_deseable=NULL:
--
-- SELECT COUNT(*) FROM metricas WHERE direccion_deseable IS NOT NULL;
-- Resultado esperado: 0
--
-- Para listar métricas existentes y preparar configuración de dirección:
--
-- SELECT codigo, nombre, categoria_id, direccion_deseable, valor_objetivo 
-- FROM metricas 
-- ORDER BY categoria_id, nombre;
