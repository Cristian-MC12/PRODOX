-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- V23 — Tabla para resultados de cálculos de métricas

-- ============================================================
-- Resultados de Cálculos de Métricas
-- ============================================================
-- Almacena resultados de métricas calculadas con trazabilidad completa.
-- INMUTABLE: no se actualizan, se crean nuevos registros si se recalcula.

CREATE TABLE resultados_metricas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Referencias
    proyecto_id UUID NOT NULL REFERENCES proyectos(id) ON DELETE CASCADE,
    metrica_id UUID NOT NULL REFERENCES metricas(id) ON DELETE CASCADE,
    sprint_id UUID NOT NULL REFERENCES sprints(id) ON DELETE CASCADE,
    
    -- Trazabilidad de parametrización
    parametrizacion_id UUID NOT NULL REFERENCES metric_parametrizaciones(id),
    parametrizacion_version INTEGER NOT NULL,
    
    -- Configuración del cálculo
    tipo_calculo VARCHAR(20) NOT NULL 
        CHECK (tipo_calculo IN ('directo', 'suma', 'promedio', 'formula')),
    expresion_utilizada TEXT,
    valores_utilizados JSONB NOT NULL,
    
    -- Resultado
    resultado NUMERIC(15, 4) NOT NULL,
    unidad VARCHAR(50),
    
    -- Estado
    estado VARCHAR(20) NOT NULL DEFAULT 'calculado'
        CHECK (estado IN ('calculado', 'error', 'incompleto')),
    mensaje_error TEXT,
    
    -- Auditoría
    calculado_por VARCHAR(255) NOT NULL,
    calculado_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Índices para rendimiento
-- ============================================================

-- Búsqueda por proyecto y sprint (dashboard)
CREATE INDEX idx_resultados_proyecto_sprint 
    ON resultados_metricas(proyecto_id, sprint_id);

-- Búsqueda por métrica y parametrización (histórico)
CREATE INDEX idx_resultados_metrica_parametrizacion
    ON resultados_metricas(metrica_id, parametrizacion_id, parametrizacion_version);

-- Búsqueda por sprint (resumen de sprint)
CREATE INDEX idx_resultados_sprint
    ON resultados_metricas(sprint_id, calculado_at DESC);

-- Búsqueda del último cálculo por métrica+sprint
CREATE INDEX idx_resultados_ultimo_calculo
    ON resultados_metricas(metrica_id, sprint_id, calculado_at DESC);

-- ============================================================
-- Comentarios de documentación
-- ============================================================

COMMENT ON TABLE resultados_metricas IS 
    'Resultados de cálculos de métricas con trazabilidad completa. Inmutable: no se actualizan, se crean nuevos registros.';

COMMENT ON COLUMN resultados_metricas.parametrizacion_id IS 
    'ID de la parametrización utilizada para el cálculo.';

COMMENT ON COLUMN resultados_metricas.parametrizacion_version IS 
    'Versión de la parametrización (1, 2, 3...). Permite reproducibilidad histórica.';

COMMENT ON COLUMN resultados_metricas.tipo_calculo IS 
    'Tipo de cálculo: directo (valor registrado), suma, promedio, formula (expresión aritmética).';

COMMENT ON COLUMN resultados_metricas.expresion_utilizada IS 
    'Expresión aritmética utilizada (solo para tipo formula). Ej: $${var_a} / $${var_b} * 100';

COMMENT ON COLUMN resultados_metricas.valores_utilizados IS 
    'Snapshot de valores utilizados en formato JSON para reproducibilidad. Ej: {"var_id": 42.0}';

COMMENT ON COLUMN resultados_metricas.resultado IS 
    'Resultado del cálculo con precisión de 4 decimales.';

COMMENT ON COLUMN resultados_metricas.estado IS 
    'Estado: calculado (éxito), error (fallo), incompleto (datos faltantes).';

