/**
 * Métrica de planeación: define QUÉ se va a medir y CÓMO,
 * sin valores reales (esos llegan en la fase de Ejecución).
 */
export interface MetricaPlan {
  id?: string;
  factorId: string;
  factorName?: string;
  factorCategory?: string;
  sprintName: string;
  unidad: string;           // %, pts, días, defectos, etc.
  valorMeta: number;        // valor objetivo/esperado al cierre del sprint
  descripcion: string;      // cómo se va a recopilar el dato
  fuente: string;           // Jira / GitHub / Manual
  status: 'borrador' | 'aprobada' | 'rechazada';
  rechazadoMotivo?: string;
  creadoEn?: string;
}
