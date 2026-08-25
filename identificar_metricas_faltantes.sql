-- =====================================================
-- IDENTIFICAR LAS 9 MÉTRICAS FALTANTES EN EL ANÁLISIS
-- =====================================================

SELECT 
    m.codigo,
    m.nombre,
    m.descripcion,
    m.factor,
    m.categoria_id
FROM metricas m
WHERE m.codigo NOT IN (
    -- 6 Aprendizaje Organizacional
    'FLX-FAT-01', 'FLX-FAT-02', 'FLX-GAE-01', 'FLX-GAE-02', 'FLX-NMP-01', 'FLX-NMP-02',
    -- 2 Atmósfera y Configuración
    'FSH-ATM-01', 'FSH-CFG-01',
    -- 2 Compromiso
    'FSH-CMP-01', 'FSH-CMP-02',
    -- 1 Comunicación
    'FSH-COM-01',
    -- 1 Confianza
    'FSH-CON-01',
    -- 1 Habilidades
    'FSH-HAB-01',
    -- 1 Liderazgo
    'FSH-LID-01',
    -- 1 Motivación
    'FSH-MOT-01',
    -- 1 Orgullo
    'FSH-ORG-01',
    -- 1 Poder
    'FSH-POD-01',
    -- 1 Satisfacción
    'FSH-SAT-01',
    -- 2 Calidad
    'IMP-CAL-01', 'IMP-CAL-02',
    -- 3 Establecimiento/Manejo
    'IMP-EMG-01', 'IMP-EMG-02', 'IMP-MR-01',
    -- 2 Satisfacción Cliente
    'SIG-SC-01', 'SIG-SC-02'
)
ORDER BY m.codigo;
