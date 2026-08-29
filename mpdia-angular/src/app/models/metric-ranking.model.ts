// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { EscalaEstructurada } from './seleccion.model';

export interface RankingMetrica {
  factorId:         string;
  factorNombre:     string;
  factorCategoria:  string;
  usos:             number;
  parametrizacionId: string;
}

export interface MetricParametrizacionBase extends EscalaEstructurada {
  id:                string;
  factorId:          string;
  factorNombre:      string;
  factorCategoria:   string;
  userEmail:         string;
  objetivo:          string;
  procedimiento:     string;
  indicadorVariable: string;
  escala:            string;
  metricaBaseId:     string | null;
  createdAt:         string;
  frecuenciaCaptura?: string | null;
  fuenteAcademica?:  string | null;
  formulaAcademica?: string | null;
  tipoOperacion?:    string | null;
  unidadResultado?:  string | null;
}

export interface GuardarParametrizacionRequest extends EscalaEstructurada {
  factorId?:         string | null;
  objetivo:          string;
  procedimiento:     string;
  indicadorVariable: string;
  escala:            string;
  metricaBaseId:     string | null;
  proyectoId:        string | null;
  metricaId?:        string | null;
  // FASE 11: campos académicos opcionales — sin ellos, Ejecución no puede calcular
  // la métrica aprobada por el flujo "Enviar al Scrum Master".
  tipoOperacion?:    string | null;
  formulaAcademica?: string | null;
  unidadResultado?:  string | null;
  fuenteAcademica?:  string | null;
  // Revisión de frecuencia de captura: faltaba acá, por lo que el backend nunca
  // podía recibirla y la persistía siempre como "por_sprint" (ver MetricRankingService).
  frecuenciaCaptura?: string | null;
}

export interface TopParametrizacion extends EscalaEstructurada {
  id:                string;
  userEmail:         string;
  objetivo:          string;
  procedimiento:     string;
  indicadorVariable: string;
  escala:            string;
  usos:              number;
  createdAt:         string;
  frecuenciaCaptura?: string | null;
  fuenteAcademica?:  string | null;
  formulaAcademica?: string | null;
  tipoOperacion?:    string | null;
  unidadResultado?:  string | null;
}
