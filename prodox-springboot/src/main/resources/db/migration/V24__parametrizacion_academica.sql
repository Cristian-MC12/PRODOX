-- FASE 16.9.1: Extensión de parametrización para métricas académicas
-- Autor: Cristian Santiago Martinez Cordoba — MPDIA
-- Fecha: 2026-08-16

-- Agregar campos académicos a metric_parametrizaciones
ALTER TABLE metric_parametrizaciones
ADD COLUMN fuente_academica TEXT,
ADD COLUMN formula_academica VARCHAR(500),
ADD COLUMN tipo_operacion VARCHAR(20),
ADD COLUMN unidad_resultado VARCHAR(50);

-- Índice para búsqueda por tipo de operación
CREATE INDEX idx_metric_parametrizaciones_tipo_operacion 
ON metric_parametrizaciones(tipo_operacion);

-- Comentarios
COMMENT ON COLUMN metric_parametrizaciones.fuente_academica IS 
'Fuente académica completa de la métrica (autor, año, artículo, DOI, página)';

COMMENT ON COLUMN metric_parametrizaciones.formula_academica IS 
'Fórmula matemática según la fuente académica (ej: Σ problemas_reportados)';

COMMENT ON COLUMN metric_parametrizaciones.tipo_operacion IS 
'Tipo de operación: SUMA | PROMEDIO | DIRECTO | FORMULA';

COMMENT ON COLUMN metric_parametrizaciones.unidad_resultado IS 
'Unidad del resultado (problemas, puntos, %, días, etc.)';
