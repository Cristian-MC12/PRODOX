export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api',
  backendUrl: 'http://localhost:8080',
  // UUIDs de métricas base para desarrollo local
  metricasBase: [
    'dde97e2b-1b25-493e-9273-a6b59564b053', // Impedimentos por sprint
    '2ba0cf34-0bec-4e7d-8dc5-40795f050ec9', // Problemas reportados por el cliente
    '40beffdf-13f4-4772-8820-4df93fae525c', // Deuda técnica gestionada
    'beb22a94-0e1b-496a-8b9e-a08a8f6d77c3', // Aprendizaje organizacional (FAT)
    'ec0d74fe-0bf4-4970-af89-dcaa0736c8ed', // Defectos
  ]
};
