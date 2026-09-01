// Autor: Cristian Santiago Martinez Cordoba — PRODOX

export interface MetricaDto {
  id:          string;
  codigo:      string;
  nombre:      string;
  descripcion: string | null;
  categoria:   'Calidad' | 'Productividad' | 'Cumplimiento' | 'Flexibilidad' | 'Sociohumano' | string;
}
