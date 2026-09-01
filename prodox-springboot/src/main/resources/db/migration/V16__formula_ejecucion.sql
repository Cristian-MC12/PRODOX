-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V16 — Agrega soporte de fórmula y frecuencia de captura a variables
--        para que la fase de Ejecución sepa qué datos pedir y cómo calcular

-- 1. Agregar columnas a variables
ALTER TABLE variables
    ADD COLUMN IF NOT EXISTS formula_texto      TEXT,
    ADD COLUMN IF NOT EXISTS formula_json       JSONB,
    ADD COLUMN IF NOT EXISTS frecuencia_captura VARCHAR(20) NOT NULL DEFAULT 'por_sprint'
        CHECK (frecuencia_captura IN ('por_sprint','semanal','diaria','ilimitada'));

-- 2. formula_json tendrá la estructura:
-- {
--   "expresion": "ISE = Critico*C + Mayor*M + Medio*Med + Menor*Men",
--   "operandos": [
--     { "clave": "Critico", "etiqueta": "Errores Críticos", "tipo": "numerico", "pesoFactor": 5 },
--     { "clave": "Mayor",   "etiqueta": "Errores Mayores",  "tipo": "numerico", "pesoFactor": 1 },
--     { "clave": "Medio",   "etiqueta": "Errores Medios",   "tipo": "numerico", "pesoFactor": 4 },
--     { "clave": "Menor",   "etiqueta": "Errores Menores",  "tipo": "numerico", "pesoFactor": 6 }
--   ],
--   "escalaResultado": "Numérico >= 0"
-- }

-- 3. Índice para buscar variables con fórmula configurada
CREATE INDEX IF NOT EXISTS idx_variables_formula ON variables((formula_json IS NOT NULL));
