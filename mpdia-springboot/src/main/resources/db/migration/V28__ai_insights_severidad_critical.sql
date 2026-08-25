-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- FASE 21 — Causa raíz demostrada de "AI Insights no genera insights":
-- V20 definió ai_insights_severidad_check permitiendo solo ('LOW','MEDIUM','HIGH'),
-- pero AgileAnalyticsService.determineTrendSeverity() (variación >30%) y el propio
-- frontend (ai-insights.component.ts: getSeverityBadgeClass/getSeverityLabel,
-- severityOrder) ya tratan 'CRITICAL' como un nivel de severidad válido y esperado.
-- Al generar un insight de riesgo con severidad CRITICAL, el INSERT viola el CHECK
-- al momento del commit de la transacción, haciendo rollback de TODOS los insights
-- generados en esa llamada (confirmado en logs: 7 insights generados en memoria,
-- 0 persistidos, HTTP 409 devuelto al frontend).
ALTER TABLE ai_insights DROP CONSTRAINT ai_insights_severidad_check;
ALTER TABLE ai_insights ADD CONSTRAINT ai_insights_severidad_check
    CHECK (severidad IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

COMMENT ON COLUMN ai_insights.severidad IS
    'Nivel de severidad: LOW (bajo impacto), MEDIUM (impacto moderado), HIGH (alto impacto), CRITICAL (impacto crítico, variación >30%)';
