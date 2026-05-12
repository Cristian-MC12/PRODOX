// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { MetricaPlan } from '../models/metrica-plan.model';

/**
 * Servicio local para métricas de planeación.
 * Persiste en localStorage. En la fase de Ejecución
 * estas definiciones se usarán para recopilar valores reales.
 */
@Injectable({ providedIn: 'root' })
export class MetricaPlanService {

  private readonly KEY = 'mpdia_metricas_plan';
  private metricas$ = new BehaviorSubject<MetricaPlan[]>(this.load());

  getAll() { return this.metricas$.asObservable(); }

  getBySprint(sprintName: string): MetricaPlan[] {
    return this.metricas$.value.filter(m => m.sprintName === sprintName);
  }

  save(m: MetricaPlan): void {
    const list = [...this.metricas$.value];
    if (m.id) {
      const idx = list.findIndex(x => x.id === m.id);
      if (idx >= 0) list[idx] = m; else list.push(m);
    } else {
      m.id = crypto.randomUUID();
      m.creadoEn = new Date().toISOString();
      m.status = 'borrador';
      list.push(m);
    }
    this.persist(list);
  }

  approve(id: string): void {
    this.updateStatus(id, 'aprobada');
  }

  reject(id: string, motivo: string): void {
    const list = this.metricas$.value.map(m =>
      m.id === id ? { ...m, status: 'rechazada' as const, rechazadoMotivo: motivo } : m
    );
    this.persist(list);
  }

  delete(id: string): void {
    this.persist(this.metricas$.value.filter(m => m.id !== id));
  }

  /** Genera métricas predefinidas para un factor (simula el Copiloto en planeación) */
  generateForFactor(factorId: string, factorName: string, factorCategory: string, sprintName: string): void {
    const templates = this.getTemplates(factorCategory, factorName);
    const list = [...this.metricas$.value];
    templates.forEach(t => {
      list.push({
        id: crypto.randomUUID(),
        factorId,
        factorName,
        factorCategory,
        sprintName,
        unidad:      t.unidad,
        valorMeta:   t.valorMeta,
        descripcion: t.descripcion,
        fuente:      t.fuente,
        status:      'borrador',
        creadoEn:    new Date().toISOString()
      });
    });
    this.persist(list);
  }

  private getTemplates(category: string, factorName: string) {
    const templates: Record<string, Array<{unidad: string; valorMeta: number; descripcion: string; fuente: string}>> = {
      'Productividad': [
        { unidad: 'pts', valorMeta: 80, descripcion: 'Story points completados vs. planificados al cierre del sprint.', fuente: 'Jira' }
      ],
      'Calidad': [
        { unidad: '%',   valorMeta: 80, descripcion: 'Porcentaje de cobertura de pruebas automatizadas sobre el código entregado.', fuente: 'GitHub' }
      ],
      'Cumplimiento': [
        { unidad: '%',   valorMeta: 90, descripcion: 'Porcentaje de objetivos del sprint alcanzados al cierre.', fuente: 'Jira' }
      ]
    };
    return templates[category] ?? [
      { unidad: '%', valorMeta: 80, descripcion: `Medición de ${factorName} durante el sprint.`, fuente: 'Manual' }
    ];
  }

  private updateStatus(id: string, status: MetricaPlan['status']): void {
    const list = this.metricas$.value.map(m => m.id === id ? { ...m, status } : m);
    this.persist(list);
  }

  private persist(list: MetricaPlan[]): void {
    localStorage.setItem(this.KEY, JSON.stringify(list));
    this.metricas$.next([...list]);
  }

  private load(): MetricaPlan[] {
    const raw = localStorage.getItem(this.KEY);
    return raw ? JSON.parse(raw) : [];
  }
}
