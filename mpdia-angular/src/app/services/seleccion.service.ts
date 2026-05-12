// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { MetricaSeleccionada, Parametrizacion } from '../models/seleccion.model';

/**
 * Servicio que gestiona las métricas seleccionadas para el sprint.
 * Persiste en localStorage. Cada entrada tiene factor + métrica + parametrización.
 */
@Injectable({ providedIn: 'root' })
export class SeleccionService {

  private readonly KEY = 'mpdia_selecciones';
  private selecciones$ = new BehaviorSubject<MetricaSeleccionada[]>(this.load());

  getAll() { return this.selecciones$.asObservable(); }

  getSnapshot(): MetricaSeleccionada[] { return this.selecciones$.value; }

  agregar(item: Omit<MetricaSeleccionada, 'id' | 'creadoEn' | 'estadoParametrizacion'>): void {
    const list = [...this.selecciones$.value];
    // Evitar duplicados (mismo factor + misma métrica)
    const existe = list.some(
      s => s.factorId === item.factorId && s.metricaNombre === item.metricaNombre
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

  limpiar(): void { this.persist([]); }

  private persist(list: MetricaSeleccionada[]): void {
    localStorage.setItem(this.KEY, JSON.stringify(list));
    this.selecciones$.next([...list]);
  }

  private load(): MetricaSeleccionada[] {
    const raw = localStorage.getItem(this.KEY);
    return raw ? JSON.parse(raw) : [];
  }
}
