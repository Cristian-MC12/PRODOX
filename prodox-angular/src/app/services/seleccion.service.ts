// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, map } from 'rxjs';
import { MetricaSeleccionada, Parametrizacion } from '../models/seleccion.model';

/**
 * Servicio que gestiona las métricas seleccionadas para el sprint.
 * Persiste en localStorage. Cada entrada tiene factor + métrica + parametrización.
 *
 * FASE 11: el almacenamiento sigue siendo una única lista global en localStorage (todas las
 * entradas de todos los proyectos), pero getAll()/getSnapshot() filtran por el proyecto activo
 * antes de exponerla, y limpiar() solo borra las entradas del proyecto activo — así una
 * selección de un proyecto nunca se ve (ni se pierde) al entrar a otro proyecto (ver
 * diagnóstico FASE 10, riesgo de datos).
 */
@Injectable({ providedIn: 'root' })
export class SeleccionService {

  private readonly KEY = 'mpdia_selecciones';
  private selecciones$ = new BehaviorSubject<MetricaSeleccionada[]>(this.load());

  getAll(): Observable<MetricaSeleccionada[]> {
    return this.selecciones$.asObservable().pipe(map(list => this.filtrarPorProyectoActivo(list)));
  }

  getSnapshot(): MetricaSeleccionada[] {
    return this.filtrarPorProyectoActivo(this.selecciones$.value);
  }

  agregar(item: Omit<MetricaSeleccionada, 'id' | 'creadoEn' | 'estadoParametrizacion'>): void {
    const list = [...this.selecciones$.value];
    // Evitar duplicados (mismo factor + misma métrica + mismo proyecto)
    const existe = list.some(
      s => s.factorId === item.factorId && s.metricaNombre === item.metricaNombre
        && s.proyectoId === item.proyectoId
    );
    if (existe) return;
    list.push({
      ...item,
      id: crypto.randomUUID(),
      creadoEn: new Date().toISOString(),
      estadoParametrizacion: 'sin_parametrizar'
    });
    this.persist(list);
  }

  quitar(id: string): void {
    this.persist(this.selecciones$.value.filter(s => s.id !== id));
  }

  parametrizar(id: string, p: Parametrizacion): void {
    const completos = [p.objetivo, p.procedimiento, p.indicadorVariable, p.escala]
      .filter(c => !!c?.trim()).length;
    const estado: MetricaSeleccionada['estadoParametrizacion'] =
      completos === 0 ? 'sin_parametrizar' :
      completos === 4 ? 'completa' : 'parcial';

    const list = this.selecciones$.value.map(s => {
      if (s.id !== id) return s;
      return { ...s, parametrizacion: p, estadoParametrizacion: estado } as MetricaSeleccionada;
    });
    this.persist(list);
  }

  /** Limpia únicamente las selecciones del proyecto activo — nunca las de otros proyectos. */
  limpiar(): void {
    const proyectoId = this.proyectoActivoId();
    const restantes = proyectoId
      ? this.selecciones$.value.filter(s => s.proyectoId !== proyectoId)
      : [];
    this.persist(restantes);
  }

  private filtrarPorProyectoActivo(list: MetricaSeleccionada[]): MetricaSeleccionada[] {
    const proyectoId = this.proyectoActivoId();
    return proyectoId ? list.filter(s => s.proyectoId === proyectoId) : list;
  }

  private proyectoActivoId(): string | null {
    try {
      const raw = localStorage.getItem('mpdia_proyecto_activo');
      return raw ? JSON.parse(raw)?.id ?? null : null;
    } catch {
      return null;
    }
  }

  private persist(list: MetricaSeleccionada[]): void {
    localStorage.setItem(this.KEY, JSON.stringify(list));
    this.selecciones$.next([...list]);
  }

  private load(): MetricaSeleccionada[] {
    const raw = localStorage.getItem(this.KEY);
    return raw ? JSON.parse(raw) : [];
  }
}
