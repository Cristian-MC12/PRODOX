-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V19 — AI Agile Copilot: historial de chat e insights automáticos

-- ===========================================================================
-- 1. TABLA: ai_chat_messages
--    Almacena el historial de conversaciones con el AI Copilot
-- ===========================================================================

CREATE TABLE ai_chat_messages (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      VARCHAR(50)  NOT NULL,
    proyecto_id  UUID         NOT NULL,
    sprint_id    UUID,
    role         VARCHAR(20)  NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Índices para consultas frecuentes
CREATE INDEX idx_ai_chat_user_proyecto ON ai_chat_messages(user_id, proyecto_id, created_at);
CREATE INDEX idx_ai_chat_proyecto ON ai_chat_messages(proyecto_id, created_at DESC);
CREATE INDEX idx_ai_chat_sprint ON ai_chat_messages(sprint_id, created_at DESC) WHERE sprint_id IS NOT NULL;

COMMENT ON TABLE ai_chat_messages IS 
    'Historial de mensajes del chat con el AI Agile Copilot';
COMMENT ON COLUMN ai_chat_messages.user_id IS 
    'ID del usuario (UUID como String) que envió o recibió el mensaje';
COMMENT ON COLUMN ai_chat_messages.role IS 
    'Rol del mensaje: user (mensaje del usuario), assistant (respuesta de IA), system (contexto del sistema)';
COMMENT ON COLUMN ai_chat_messages.sprint_id IS 
    'Sprint específico del contexto de la conversación (opcional)';

-- ===========================================================================
-- 2. TABLA: ai_insights
--    Almacena insights automáticos generados por el AI Copilot
-- ===========================================================================

CREATE TABLE ai_insights (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_id    UUID         NOT NULL,
    sprint_id      UUID,
    tipo           VARCHAR(30)  NOT NULL,
    titulo         VARCHAR(200) NOT NULL,
    descripcion    TEXT         NOT NULL,
    impacto        VARCHAR(20)  NOT NULL CHECK (impacto IN ('BAJO', 'MEDIO', 'ALTO', 'CRITICO')),
    confianza      VARCHAR(20)  NOT NULL CHECK (confianza IN ('BAJA', 'MEDIA', 'ALTA')),
    recomendacion  TEXT,
    dismissed      BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    dismissed_at   TIMESTAMPTZ
);

-- Índices para consultas frecuentes
CREATE INDEX idx_ai_insights_proyecto ON ai_insights(proyecto_id, created_at DESC);
CREATE INDEX idx_ai_insights_proyecto_activos ON ai_insights(proyecto_id, dismissed, created_at DESC);
CREATE INDEX idx_ai_insights_sprint ON ai_insights(sprint_id, created_at DESC) WHERE sprint_id IS NOT NULL;
CREATE INDEX idx_ai_insights_tipo ON ai_insights(proyecto_id, tipo, created_at DESC);

COMMENT ON TABLE ai_insights IS 
    'Insights automáticos generados por el AI Copilot: riesgos, alertas, recomendaciones';
COMMENT ON COLUMN ai_insights.tipo IS 
    'Tipo de insight: RIESGO, MEJORA, ALERTA, RECOMENDACION';
COMMENT ON COLUMN ai_insights.impacto IS 
    'Nivel de impacto del insight en el proyecto';
COMMENT ON COLUMN ai_insights.confianza IS 
    'Nivel de confianza del AI en el insight generado';
COMMENT ON COLUMN ai_insights.dismissed IS 
    'Indica si el usuario descartó o ignoró el insight';

