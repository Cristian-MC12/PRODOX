// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// Servicio que unifica actividades del proyecto desde múltiples endpoints
import { Injectable } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { Activity } from '../models/activity.model';
import { EvaluacionService } from './evaluacion.service';
import { SprintService } from './sprint.service';
import { ProjectMemberService } from './project-member.service';
import { AIInsightsService } from './ai-insights.service';
import { HistoriaUsuarioService } from './historia-usuario.service';

@Injectable({ providedIn: 'root' })
export class ActivityFeedService {

  constructor(
    private evaluacionService: EvaluacionService,
    private sprintService: SprintService,
    private memberService: ProjectMemberService,
    private insightsService: AIInsightsService,
    private historiaService: HistoriaUsuarioService
  ) {}

  /**
   * Obtiene las actividades recientes del proyecto desde múltiples fuentes.
   * Combina evaluaciones, sprints, miembros, insights e historias de usuario
   * (V39 — Product Owner) en un feed unificado. No hay tabla de
   * notificaciones nueva: igual que el resto de este servicio, se deriva
   * 100% de endpoints de lectura que ya existían para otro propósito
   * (GET /api/historias/{proyectoId}, ya usado por el Backlog).
   */
  getProjectActivities(proyectoId: string, limit: number = 10): Observable<Activity[]> {
    const requests = {
      evaluaciones: this.evaluacionService.detalle(proyectoId).pipe(
        catchError(() => of([]))
      ),
      sprints: this.sprintService.listar(proyectoId).pipe(
        catchError(() => of([]))
      ),
      miembros: this.memberService.listar(proyectoId).pipe(
        catchError(() => of([]))
      ),
      insights: this.insightsService.getProjectInsights(proyectoId).pipe(
        catchError(() => of([]))
      ),
      historias: this.historiaService.listar(proyectoId).pipe(
        catchError(() => of([]))
      )
    };

    return forkJoin(requests).pipe(
      map(({ evaluaciones, sprints, miembros, insights, historias }) => {
        const activities: Activity[] = [];
        
        // Calcular rango de fechas para filtrar actividades recientes (últimos 30 días)
        const now = new Date();
        const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 3600000);

        // Procesar evaluaciones (solo registros recientes)
        evaluaciones.forEach(ev => {
          // Procesar solo los últimos 5 registros de cada variable
          ev.registros.slice(-5).forEach(registro => {
            const fechaRegistro = new Date(registro.registradoAt);
            
            // Solo incluir si fue hace menos de 30 días
            if (fechaRegistro > thirtyDaysAgo) {
              activities.push({
                id: `eval-${registro.id}`,
                type: 'evaluation_completed',
                title: 'Evaluación completada',
                description: `Sprint ${registro.sprintNumero} · ${ev.variableNombre}`,
                timestamp: fechaRegistro,
                user: registro.userId,
                icon: 'bi-check-circle-fill',
                iconColor: 'text-success',
                metadata: { sprint: registro.sprintNumero, variable: ev.variableNombre }
              });
            }
          });
        });

        // Procesar sprints creados y cerrados (solo eventos recientes)
        sprints.forEach(sprint => {
          const fechaInicio = new Date(sprint.fechaInicio);
          
          // Sprint iniciado (solo si fue hace menos de 30 días)
          if (fechaInicio > thirtyDaysAgo) {
            activities.push({
              id: `sprint-created-${sprint.id}`,
              type: 'sprint_created',
              title: 'Sprint iniciado',
              description: `Sprint ${sprint.numero}${sprint.sprintGoal ? ' · ' + sprint.sprintGoal : ''}`,
              timestamp: fechaInicio,
              icon: 'bi-play-circle-fill',
              iconColor: 'text-info'
            });
          }

          // Sprint cerrado (solo si fue hace menos de 30 días)
          if (sprint.estado === 'finalizado' && sprint.cerradoAt) {
            const fechaCerrado = new Date(sprint.cerradoAt);
            if (fechaCerrado > thirtyDaysAgo) {
              activities.push({
                id: `sprint-closed-${sprint.id}`,
                type: 'sprint_closed',
                title: 'Sprint finalizado',
                description: `Sprint ${sprint.numero}`,
                timestamp: fechaCerrado,
                user: sprint.cerradoPor || undefined,
                icon: 'bi-check-circle',
                iconColor: 'text-primary'
              });
            }
          }
        });

        // Procesar miembros (solo los que se unieron en los últimos 30 días)
        miembros.forEach(member => {
          const fechaUnion = new Date(member.joinedAt);
          
          // Solo incluir si se unió hace menos de 30 días
          if (fechaUnion > thirtyDaysAgo) {
            activities.push({
              id: `member-${member.userId}`,
              type: 'member_joined',
              title: 'Nuevo miembro',
              description: `${member.userEmail} se unió al equipo`,
              timestamp: fechaUnion,
              icon: 'bi-person-plus-fill',
              iconColor: 'text-warning'
            });
          }
        });

        // Procesar insights de IA
        insights.forEach(insight => {
          const description = insight.description.length > 80 
            ? insight.description.substring(0, 80) + '...' 
            : insight.description;
            
          activities.push({
            id: `insight-${insight.id}`,
            type: 'ai_insight_generated',
            title: 'Insight de IA generado',
            description: description,
            timestamp: new Date(insight.createdAt),
            icon: 'bi-lightbulb-fill',
            iconColor: 'text-info',
            metadata: { type: insight.type }
          });
        });

        // Procesar historias de usuario (V39 — creadas o completadas recientemente).
        // "completada" usa updatedAt como aproximación (igual criterio que
        // sprint_created usa fechaInicio): no distingue completada-y-luego-
        // reeditada de completada-ahora-mismo, aceptable para un feed informativo.
        historias.forEach(h => {
          const fechaCreacion = new Date(h.createdAt);
          if (fechaCreacion > thirtyDaysAgo) {
            activities.push({
              id: `historia-created-${h.id}`,
              type: 'historia_created',
              title: 'Historia creada',
              description: h.titulo,
              timestamp: fechaCreacion,
              user: h.creadoPor,
              icon: 'bi-file-earmark-plus-fill',
              iconColor: 'text-primary',
              metadata: { prioridad: h.prioridad }
            });
          }

          if (h.estado === 'completada') {
            const fechaActualizacion = new Date(h.updatedAt);
            if (fechaActualizacion > thirtyDaysAgo) {
              activities.push({
                id: `historia-completed-${h.id}`,
                type: 'historia_completed',
                title: 'Historia completada',
                description: h.titulo,
                timestamp: fechaActualizacion,
                icon: 'bi-check-square-fill',
                iconColor: 'text-success'
              });
            }
          }
        });

        // Ordenar por timestamp descendente y limitar
        return activities
          .sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime())
          .slice(0, limit);
      })
    );
  }
}
