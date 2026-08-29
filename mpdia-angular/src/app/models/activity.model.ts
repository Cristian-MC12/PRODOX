// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// Modelo unificado para el feed de actividad del Dashboard

export type ActivityType = 
  | 'evaluation_completed'
  | 'sprint_created'
  | 'sprint_closed'
  | 'member_joined'
  | 'ai_insight_generated'
  | 'metric_parametrized';

export interface Activity {
  id: string;
  type: ActivityType;
  title: string;
  description: string;
  timestamp: Date;
  user?: string;
  icon: string;
  iconColor: string;
  metadata?: Record<string, any>;
}
