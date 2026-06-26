// Autor: Cristian Santiago Martinez Cordoba — MPDIA

export interface MetricaDto {
  id:          string;
  codigo:      string;
  nombre:      string;
  descripcion: string | null;
  categoria:   'Calidad' | 'Productividad' | 'Cumplimiento' | 'Flexibilidad' | 'Sociohumano' | string;
}
