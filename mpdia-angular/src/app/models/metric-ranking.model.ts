// Autor: Cristian Santiago Martinez Cordoba — MPDIA

export interface RankingMetrica {
  factorId:         string;
  factorNombre:     string;
  factorCategoria:  string;
  usos:             number;
  parametrizacionId: string;
}

export interface MetricParametrizacionBase {
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
}

export interface GuardarParametrizacionRequest {
  factorId?:         string | null;
  objetivo:          string;
  procedimiento:     string;
  indicadorVariable: string;
  escala:            string;
  metricaBaseId:     string | null;
  proyectoId:        string | null;
  metricaId?:        string | null;
}

export interface TopParametrizacion {
  id:                string;
  userEmail:         string;
  objetivo:          string;
  procedimiento:     string;
  indicadorVariable: string;
  escala:            string;
  usos:              number;
  createdAt:         string;
}
