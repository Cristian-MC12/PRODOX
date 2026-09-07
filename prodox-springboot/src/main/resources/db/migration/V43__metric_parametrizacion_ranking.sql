-- V43 — Ranking de parametrizaciones por métrica, identificado por
-- configuración equivalente (fingerprint) — contador de USOS REALES
-- Autor: Cristian Santiago Martinez Cordoba — PRODOX
--
-- PROBLEMA CORREGIDO:
-- getTop3ByMetricaId() mostraba una fila por AUTOR (una por usuario que
-- hubiera guardado la métrica) con el mismo "usosTotales" (conteo global
-- de TODAS las filas de metric_parametrizaciones de esa métrica) repetido
-- en cada una — si tres usuarios guardaban la misma configuración,
-- aparecían tres filas idénticas mostrando el mismo número, en vez de una
-- sola fila con un contador real de selecciones "Usar".
--
-- Una corrección intermedia (no aplicada en producción) agrupó las filas
-- en memoria por configuración equivalente, mostrando una sola fila con
-- usos = cantidad de filas equivalentes existentes. Se descartó porque
-- una fila de metric_parametrizaciones representa una versión guardada
-- por proyecto, no necesariamente una pulsación real de "Usar" (puede
-- venir de datos de prueba, ediciones manuales, reenvíos, etc.) — contar
-- filas no es lo mismo que contar usos reales.
--
-- SOLUCIÓN (esta migración):
-- Tabla nueva, aislada, con un contador REAL de eventos de uso,
-- independiente de cuántas filas físicas existan en metric_parametrizaciones.
-- usos = cantidad de veces que se completó exitosamente un guardado vía
-- "Usar" DESDE que existe esta tabla (sin backfill retroactivo — ver
-- informe de diseño: contar filas históricas habría inventado usos sin
-- evidencia real de que correspondan a selecciones "Usar").
--
-- NO modifica metric_parametrizaciones (sigue igual, sus filas históricas,
-- incluidos los 42 duplicados detectados en auditoría, permanecen intactas).
-- NO modifica metric_uso_ranking (flujo legacy por factor, sin cambios).
-- NO depende de ninguna columna agregada por la V42 aplicada solo en el
-- entorno local de desarrollo (no comiteada a Git) — esta migración
-- funciona de forma independiente, aplicada directamente sobre el esquema
-- de V1 a V41.

CREATE TABLE IF NOT EXISTS metric_parametrizacion_ranking (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metrica_id                   UUID NOT NULL
                                  REFERENCES metricas(id) ON DELETE CASCADE,
    fingerprint                  CHAR(64) NOT NULL,
    parametrizacion_canonica_id  UUID NOT NULL
                                  REFERENCES metric_parametrizaciones(id) ON DELETE CASCADE,
    usos                         INTEGER NOT NULL DEFAULT 0,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ux_metric_param_ranking_metrica_fingerprint
        UNIQUE (metrica_id, fingerprint),
    CONSTRAINT chk_metric_param_ranking_usos_no_negativo
        CHECK (usos >= 0),
    CONSTRAINT chk_metric_param_ranking_fingerprint_hex
        CHECK (fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE INDEX IF NOT EXISTS idx_metric_param_ranking_metrica_usos
    ON metric_parametrizacion_ranking (metrica_id, usos DESC);

COMMENT ON TABLE metric_parametrizacion_ranking IS
    'Ranking GLOBAL (todas las metricas del catalogo, sin distincion de '
    'proyecto) de popularidad de configuraciones de parametrizacion '
    'equivalentes. usos = cantidad de selecciones "Usar" guardadas '
    'exitosamente DESDE que esta tabla existe (nunca un conteo '
    'retroactivo de filas historicas de metric_parametrizaciones). Sin '
    'columnas de email/usuario: el autor se obtiene, si se necesita, via '
    'JOIN a parametrizacion_canonica_id -> metric_parametrizaciones.';

COMMENT ON COLUMN metric_parametrizacion_ranking.fingerprint IS
    'SHA-256 hex (64 caracteres) de los campos sustantivos de la '
    'configuracion — ver FingerprintUtil.calcularFingerprint(), mismos '
    'campos que MetricRankingService.esMismoContenido(). Nunca incluye '
    'autor/proyecto/estado/fecha/id/usos.';

COMMENT ON COLUMN metric_parametrizacion_ranking.parametrizacion_canonica_id IS
    'Referencia a la metric_parametrizaciones que disparo la CREACION de '
    'esta entrada de ranking (el primer uso registrado despues del '
    'despliegue de esta tabla, no necesariamente la fila mas antigua '
    'historicamente si existian filas equivalentes previas sin uso '
    'registrado). Nunca se reasigna: preserva el autor original aunque '
    'otros usuarios reutilicen la configuracion despues.';

-- Sin backfill. Sin DML. Tabla vacia al terminar esta migracion — por
-- decision explicita (corte limpio, sin inferir usos historicos).
