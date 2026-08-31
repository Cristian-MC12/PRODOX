-- Habilita la captura individual por miembro con agregación configurable.
-- Nullable: no rompe variables/parametrizaciones existentes. Solo se exige
-- (falla explicita en CalculoMetricaService) cuando una variable 'individual'
-- tiene mas de un registro para el periodo y su tipoOperacion es DIRECTO o
-- FORMULA -- para SUMA/PROMEDIO a nivel de metrica, o para variables
-- 'grupal', este campo no se usa.
ALTER TABLE variables
    ADD COLUMN agregacion_miembros VARCHAR(20)
        CHECK (agregacion_miembros IN ('SUMA', 'PROMEDIO', 'CONTEO', 'MIN', 'MAX'));

COMMENT ON COLUMN variables.agregacion_miembros IS
    'Regla para reducir a un unico valor los registros de distintos miembros de una variable individual antes de DIRECTO/FORMULA. Null = sin configurar.';
