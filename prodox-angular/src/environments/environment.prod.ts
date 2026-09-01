export const environment = {
  production: true,
  apiBaseUrl: 'https://prodox-production.up.railway.app/api',
  // UUIDs de métricas base para producción (Railway)
  metricasBase: [
    'd0a56045-a0f5-47bf-9e96-a2056a99c709', // Defectos (SIG-CE-02)
    '9dd2745a-63ee-42db-8f00-ec6ef279532d', // Deuda técnica gestionada (FLX-GAE-02)
    '7e73e324-c4ef-44f2-9111-7c64e1226c1f', // Aprendizaje organizacional (FAT)
    'c0fbef4a-6103-4d57-8b5a-b7c7e55c7fd8', // Defectos encontrados (IMP-CAL-01)
    'a0327bb1-362c-4f3e-9bc0-a3c34aad9bf0', // Errores en producción (IMP-CAL-02)
  ]
};
