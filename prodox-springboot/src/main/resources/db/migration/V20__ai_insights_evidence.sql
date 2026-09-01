-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V20 — AI Insights: agregar soporte para evidencia estructurada (Fase 13)

-- ===========================================================================
-- ACTUALIZAR TABLA: ai_insights
-- Agregar campos para evidencia estructurada y mejor clasificación
-- ===========================================================================

-- 1. Renombrar 'impacto' a 'severidad' (mejor nomenclatura)
ALTER TABLE ai_insights RENAME COLUMN impacto TO severidad;

-- 2. Actualizar constraint de severidad
ALTER TABLE ai_insights DROP CONSTRAINT ai_insights_impacto_check;
ALTER TABLE ai_insights ADD CONSTRAINT ai_insights_severidad_check
    CHECK (severidad IN ('LOW', 'MEDIUM', 'HIGH'));

-- 3. Actualizar valores existentes de severidad (mapeo español -> inglés)
UPDATE ai_insights SET severidad = 'LOW' WHERE severidad = 'BAJO';
UPDATE ai_insights SET severidad = 'MEDIUM' WHERE severidad = 'MEDIO';
UPDATE ai_insights SET severidad = 'HIGH' WHERE severidad IN ('ALTO', 'CRITICO');

-- 4. Actualizar constraint de confianza
ALTER TABLE ai_insights DROP CONSTRAINT ai_insights_confianza_check;
ALTER TABLE ai_insights ADD CONSTRAINT ai_insights_confianza_check
    CHECK (confianza IN ('LOW', 'MEDIUM', 'HIGH'));

-- 5. Actualizar valores existentes de confianza (mapeo español -> inglés)
UPDATE ai_insights SET confianza = 'LOW' WHERE confianza = 'BAJA';
UPDATE ai_insights SET confianza = 'MEDIUM' WHERE confianza = 'MEDIA';
UPDATE ai_insights SET confianza = 'HIGH' WHERE confianza = 'ALTA';

-- 6. Agregar columna para evidencia estructurada en JSON
ALTER TABLE ai_insights ADD COLUMN evidence_json JSONB;

COMMENT ON COLUMN ai_insights.evidence_json IS 
    'Evidencia cuantitativa estructurada que respalda el insight (valores numéricos calculados por el backend)';

-- 7. Agregar columna para categoría afectada
ALTER TABLE ai_insights ADD COLUMN categoria_afectada VARCHAR(50);

COMMENT ON COLUMN ai_insights.categoria_afectada IS 
    'Categoría de métrica afectada: Calidad, Productividad, Cumplimiento, etc.';

-- 8. Actualizar tipos de insight (mejores nombres)
-- Los valores existentes se mantienen compatibles, pero ahora se prefiere:
-- 'TREND', 'ANOMALY', 'RISK', 'COMPARISON'

COMMENT ON COLUMN ai_insights.tipo IS 
    'Tipo de insight: TREND (tendencia), ANOMALY (anomalía), RISK (riesgo), COMPARISON (comparación)';

-- 9. Crear índice en evidence_json para búsquedas
CREATE INDEX idx_ai_insights_evidence ON ai_insights USING GIN (evidence_json) 
    WHERE evidence_json IS NOT NULL;

-- 10. Crear índice en categoría_afectada
CREATE INDEX idx_ai_insights_categoria ON ai_insights(proyecto_id, categoria_afectada, created_at DESC)
    WHERE categoria_afectada IS NOT NULL;

-- ===========================================================================
-- COMENTARIOS ACTUALIZADOS
-- ===========================================================================

COMMENT ON TABLE ai_insights IS 
    'Insights automáticos generados por el AI Copilot con evidencia estructurada (Fase 13)';

COMMENT ON COLUMN ai_insights.severidad IS 
    'Nivel de severidad: LOW (bajo impacto), MEDIUM (impacto moderado), HIGH (alto impacto)';

COMMENT ON COLUMN ai_insights.confianza IS 
    'Nivel de confianza del AI: LOW (datos limitados), MEDIUM (datos suficientes), HIGH (datos extensos y consistentes)';
