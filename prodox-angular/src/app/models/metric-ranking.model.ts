// Autor: Cristian Santiago Martinez Cordoba — PRODOX
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
  /** Revisión de captura por parametrización: "EQUIPO" | "SCRUM_MASTER". */
  responsableCaptura?: string | null;
}

/**
 * Revisión de navegación: forma mínima de una parametrización pendiente de
 * revisión, tal como la devuelve GET /metric-ranking/pendientes — usada por
 * la notificación de aprobación pendiente (además de Planeación/Resumen/
 * Verificación, que ya consumían este mismo endpoint por su cuenta).
 */
export interface PendienteNotificacion {
  id:              string;
  factorId:        string;
  factorNombre:    string;
  factorCategoria: string;
  userEmail:       string;
  metricaId?:      string | null;
  createdAt:       string;
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
